"""
LangSmith Prompt Management & A/B Testing Framework

Provides centralized prompt versioning, A/B testing, and performance tracking
integrated with LangSmith Hub.

Features:
- Pull prompts from LangSmith Hub by tag (e.g., "prod", "prod-a", "prod-b")
- A/B testing with configurable traffic splits
- Automatic tracking of prompt versions in workflow logs
- Fallback to local prompts if LangSmith is unavailable
- Support for prompt templates with variable substitution
"""

import logging
import os
import random
from dataclasses import dataclass
from enum import Enum
from typing import Any, Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)


class PromptSource(Enum):
    """Source of the prompt"""

    LANGSMITH_HUB = "langsmith_hub"
    LOCAL_FALLBACK = "local_fallback"
    HARDCODED = "hardcoded"


@dataclass
class PromptConfig:
    """Configuration for a prompt"""

    name: str  # Prompt name in LangSmith Hub (e.g., "tutor-chat-answer")
    tag: str = "prod"  # Tag to pull (e.g., "prod", "prod-a", "v2")
    fallback_prompt: Optional[str] = None  # Fallback if LangSmith unavailable
    template_vars: Optional[Dict[str, Any]] = (
        None  # Variables for template substitution
    )


@dataclass
class ABTestConfig:
    """Configuration for A/B testing"""

    name: str  # Test name (e.g., "work-feedback-evaluate")
    variants: List[Tuple[str, float]]  # List of (tag, weight) tuples
    # Example: [("prod-a", 0.5), ("prod-b", 0.5)] for 50/50 split

    def __post_init__(self):
        """Validate that weights sum to 1.0"""
        total_weight = sum(weight for _, weight in self.variants)
        if not (0.99 <= total_weight <= 1.01):  # Allow small floating point errors
            raise ValueError(f"Variant weights must sum to 1.0, got {total_weight}")


@dataclass
class PromptResult:
    """Result of fetching a prompt"""

    content: str  # The prompt text
    source: PromptSource  # Where it came from
    name: str  # Prompt name
    tag: str  # Tag/version used
    metadata: Dict[str, Any]  # Additional metadata (commit hash, etc.)


class PromptManager:
    """
    Centralized prompt management with LangSmith Hub integration

    Usage:
        # Single prompt
        manager = PromptManager()
        prompt = manager.get_prompt(PromptConfig(
            name="tutor-chat-answer",
            tag="prod"
        ))

        # A/B testing
        prompt = manager.get_prompt_with_ab_test(ABTestConfig(
            name="work-feedback-evaluate",
            variants=[("prod-a", 0.5), ("prod-b", 0.5)]
        ))
    """

    def __init__(self):
        """Initialize the prompt manager"""
        self._client = None
        self._langsmith_available = self._check_langsmith_availability()

        if self._langsmith_available:
            logger.info("✓ PromptManager initialized with LangSmith Hub integration")
        else:
            logger.warning(
                "⚠ PromptManager initialized without LangSmith (using fallbacks only)"
            )

    def _check_langsmith_availability(self) -> bool:
        """Check if LangSmith is available and configured"""
        try:
            from langsmith import Client

            api_key = os.getenv("LANGSMITH_API_KEY")
            if not api_key:
                logger.info("LangSmith API key not found, using fallback prompts")
                return False

            # Initialize client
            self._client = Client()
            return True

        except ImportError:
            logger.warning("langsmith package not installed, using fallback prompts")
            return False
        except Exception as e:
            logger.error(f"Error initializing LangSmith client: {str(e)}")
            return False

    def get_prompt(
        self, config: PromptConfig, metadata: Optional[Dict[str, Any]] = None
    ) -> PromptResult:
        """
        Get a prompt from LangSmith Hub or fallback

        Args:
            config: Prompt configuration
            metadata: Additional metadata to attach

        Returns:
            PromptResult with the prompt content and metadata
        """
        # Try LangSmith Hub first
        if self._langsmith_available:
            try:
                prompt_result = self._fetch_from_langsmith(config)
                if prompt_result:
                    return prompt_result
            except Exception as e:
                logger.error(f"Error fetching prompt from LangSmith: {str(e)}")

        # Fallback to local prompt
        return self._fetch_fallback(config)

    def get_prompt_with_ab_test(
        self,
        ab_config: ABTestConfig,
        fallback_prompt: Optional[str] = None,
        template_vars: Optional[Dict[str, Any]] = None,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> PromptResult:
        """
        Get a prompt using A/B testing (random variant selection)

        Args:
            ab_config: A/B test configuration with variants and weights
            fallback_prompt: Fallback prompt if LangSmith unavailable
            template_vars: Variables for template substitution
            metadata: Additional metadata to attach

        Returns:
            PromptResult with the selected variant
        """
        # Select variant based on weights
        selected_tag = self._select_variant(ab_config)

        logger.info(f"A/B Test '{ab_config.name}': Selected variant '{selected_tag}'")

        # Fetch the selected variant
        config = PromptConfig(
            name=ab_config.name,
            tag=selected_tag,
            fallback_prompt=fallback_prompt,
            template_vars=template_vars,
        )

        result = self.get_prompt(config, metadata)

        # Add A/B test metadata
        result.metadata["ab_test"] = {
            "test_name": ab_config.name,
            "selected_variant": selected_tag,
            "variants": [tag for tag, _ in ab_config.variants],
        }

        return result

    def _select_variant(self, ab_config: ABTestConfig) -> str:
        """
        Select a variant based on configured weights

        Uses weighted random selection
        """
        rand = random.random()
        cumulative = 0.0

        for tag, weight in ab_config.variants:
            cumulative += weight
            if rand < cumulative:
                return tag

        # Fallback to first variant (shouldn't happen if weights sum to 1.0)
        return ab_config.variants[0][0]

    def _fetch_from_langsmith(self, config: PromptConfig) -> Optional[PromptResult]:
        """
        Fetch prompt from LangSmith Hub

        Args:
            config: Prompt configuration

        Returns:
            PromptResult or None if not found
        """
        try:
            # Pull prompt with tag
            prompt_ref = f"{config.name}:{config.tag}"

            logger.debug(f"Fetching prompt from LangSmith Hub: {prompt_ref}")

            # LangSmith returns a prompt object (different structure depending on type)
            prompt_obj = self._client.pull_prompt(prompt_ref)

            # Extract prompt content (handling different prompt types)
            content = self._extract_prompt_content(prompt_obj)

            # Apply template variables if provided
            if config.template_vars:
                content = self._apply_template_vars(content, config.template_vars)

            # Get metadata from prompt object
            metadata = {
                "commit_hash": getattr(prompt_obj, "commit_hash", None),
                "created_at": getattr(prompt_obj, "created_at", None),
                "is_public": getattr(prompt_obj, "is_public", None),
            }

            return PromptResult(
                content=content,
                source=PromptSource.LANGSMITH_HUB,
                name=config.name,
                tag=config.tag,
                metadata=metadata,
            )

        except Exception as e:
            logger.error(f"Error pulling prompt '{config.name}:{config.tag}': {str(e)}")
            return None

    def _extract_prompt_content(self, prompt_obj: Any) -> str:
        """
        Extract prompt content from LangSmith prompt object

        Handles different prompt types (ChatPromptTemplate, PromptTemplate, etc.)
        """
        try:
            # ChatPromptTemplate (most common for LangGraph)
            if hasattr(prompt_obj, "messages"):
                # Extract from last message (usually the user message)
                messages = prompt_obj.messages
                if messages:
                    last_message = messages[-1]
                    if hasattr(last_message, "prompt"):
                        return last_message.prompt.template
                    elif hasattr(last_message, "content"):
                        return last_message.content

            # PromptTemplate
            if hasattr(prompt_obj, "template"):
                return prompt_obj.template

            # String prompt
            if isinstance(prompt_obj, str):
                return prompt_obj

            # Fallback: convert to string
            logger.warning(
                f"Unknown prompt type: {type(prompt_obj)}, converting to string"
            )
            return str(prompt_obj)

        except Exception as e:
            logger.error(f"Error extracting prompt content: {str(e)}")
            return str(prompt_obj)

    def _apply_template_vars(self, content: str, vars: Dict[str, Any]) -> str:
        """
        Apply template variables to prompt content

        Supports Python format strings: {variable_name}
        """
        try:
            return content.format(**vars)
        except KeyError as e:
            logger.warning(f"Template variable not found: {e}")
            return content
        except Exception as e:
            logger.error(f"Error applying template vars: {str(e)}")
            return content

    def _fetch_fallback(self, config: PromptConfig) -> PromptResult:
        """
        Fetch fallback prompt (local or hardcoded)

        Args:
            config: Prompt configuration

        Returns:
            PromptResult with fallback content
        """
        content = config.fallback_prompt or self._get_hardcoded_fallback(config.name)

        # Apply template variables if provided
        if config.template_vars:
            content = self._apply_template_vars(content, config.template_vars)

        source = (
            PromptSource.LOCAL_FALLBACK
            if config.fallback_prompt
            else PromptSource.HARDCODED
        )

        return PromptResult(
            content=content,
            source=source,
            name=config.name,
            tag=config.tag,
            metadata={"fallback": True},
        )

    def _get_hardcoded_fallback(self, name: str) -> str:
        """
        Get hardcoded fallback prompt

        This ensures the system always works even without LangSmith
        """
        return f"[FALLBACK PROMPT: {name}]\nThis is a fallback prompt. Please configure LangSmith or provide a fallback_prompt."

    def push_prompt(
        self,
        name: str,
        content: str,
        tags: Optional[List[str]] = None,
        description: Optional[str] = None,
        is_public: bool = False,
    ) -> bool:
        """
        Push a prompt to LangSmith Hub

        Args:
            name: Prompt name
            content: Prompt content
            tags: Tags to apply (e.g., ["prod", "v2"])
            description: Prompt description
            is_public: Whether to make the prompt public

        Returns:
            True if successful, False otherwise
        """
        if not self._langsmith_available:
            logger.error("Cannot push prompt: LangSmith not available")
            return False

        try:
            # Push to LangSmith Hub
            self._client.push_prompt(
                prompt_name=name,
                object=content,
                tags=tags or [],
                description=description,
                is_public=is_public,
            )

            logger.info(f"✓ Pushed prompt '{name}' to LangSmith Hub with tags: {tags}")
            return True

        except Exception as e:
            logger.error(f"Error pushing prompt '{name}': {str(e)}")
            return False


# Global singleton instance
_prompt_manager_instance = None


def get_prompt_manager() -> PromptManager:
    """
    Get the global PromptManager instance (singleton pattern)

    Returns:
        PromptManager instance
    """
    global _prompt_manager_instance

    if _prompt_manager_instance is None:
        _prompt_manager_instance = PromptManager()

    return _prompt_manager_instance
