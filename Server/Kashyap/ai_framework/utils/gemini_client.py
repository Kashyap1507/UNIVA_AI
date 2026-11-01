"""
Gemini Client with Instructor Integration
Provides type-safe, structured outputs for all Gemini API calls
Supports streaming for real-time response delivery
"""

import json
import logging
import os
from typing import Any, Dict, Generator, Optional, Type, TypeVar

import google.generativeai as genai
import instructor
from instructor import Mode, from_gemini
from pydantic import BaseModel

from app.utils import usage_meter

# Type variable for Pydantic models
T = TypeVar("T", bound=BaseModel)


class GeminiInstructorClient:
    """
    Wrapper around Google Gemini API with Instructor integration
    Provides structured, type-safe responses using Pydantic models
    """

    def __init__(
        self, model_name: str = "gemini-2.5-flash", api_key: Optional[str] = None
    ):
        """
        Initialize Gemini client with Instructor

        Args:
            model_name: Gemini model to use (default: gemini-2.5-flash)
            api_key: Optional API key (defaults to env var GOOGLE_GEMINI_API_KEY)
        """
        self.model_name = model_name
        self.logger = logging.getLogger(__name__)

        # Get API key
        self.api_key = api_key or os.getenv("GOOGLE_GEMINI_API_KEY")
        if not self.api_key:
            raise ValueError(
                "Google Gemini API key not found. "
                "Set GOOGLE_GEMINI_API_KEY environment variable."
            )

        # Configure Gemini
        genai.configure(api_key=self.api_key)

        # Create base Gemini model
        self.model = genai.GenerativeModel(self.model_name)

        # Wrap with Instructor for structured outputs
        self.client = instructor.from_gemini(
            client=genai.GenerativeModel(self.model_name), mode=Mode.GEMINI_JSON
        )
        # Meter real token usage on every attempt (retries included) for cost tracking
        self.client.on("completion:response", lambda response: usage_meter.add_response(self.model_name, response))

        self.logger.info(
            f"Initialized Gemini Instructor client with model: {model_name}"
        )

    def generate_structured(
        self,
        response_model: Type[T],
        prompt: str,
        image_data: Optional[str] = None,
        max_retries: int = 3,
        mime_type: str = "image/jpeg",
        images: Optional[list] = None,
        **kwargs,
    ) -> T:
        """
        Generate structured response using Instructor

        Args:
            response_model: Pydantic model class for response structure
            prompt: The prompt text
            image_data: Optional base64 encoded image (or PDF, see mime_type)
            max_retries: Number of retry attempts for validation failures
            mime_type: MIME type of image_data (image/jpeg, image/png, application/pdf)
            **kwargs: Additional arguments passed to Gemini

        Returns:
            Instance of response_model with validated data

        Raises:
            ValidationError: If response doesn't match schema after retries
            Exception: For other API errors
        """
        try:
            # Prepare content
            content = []

            # Add text prompt
            content.append(prompt)

            # Add image if provided
            if image_data:
                content.append({"mime_type": mime_type, "data": image_data})
            # Several images: list of (base64_data, mime_type)
            for data, mime in images or []:
                content.append({"mime_type": mime, "data": data})

            # Generate with Instructor (automatic validation)
            response = self.client.create(
                response_model=response_model,
                messages=[
                    {"role": "user", "content": content if len(content) > 1 else prompt}
                ],
                max_retries=max_retries,
                **kwargs,
            )

            self.logger.info(
                f"Successfully generated structured response: {response_model.__name__}"
            )

            return response

        except Exception as e:
            self.logger.error(
                f"Error generating structured response: {str(e)}", exc_info=True
            )
            raise

    def generate_raw(
        self,
        prompt: str,
        image_data: Optional[str] = None,
        mime_type: str = "image/jpeg",
        **kwargs,
    ) -> str:
        """
        Generate raw text response (without Instructor validation)
        Use for cases where structured output isn't needed

        Args:
            prompt: The prompt text
            image_data: Optional base64 encoded image
            **kwargs: Additional arguments passed to Gemini

        Returns:
            Raw text response from Gemini
        """
        try:
            # Prepare content
            content = [prompt]

            # Add image if provided
            if image_data:
                content.append({"mime_type": mime_type, "data": image_data})

            # Generate with base Gemini model
            response = self.model.generate_content(content, **kwargs)
            usage_meter.add_response(self.model_name, response)

            return response.text

        except Exception as e:
            self.logger.error(f"Error generating raw response: {str(e)}", exc_info=True)
            raise

    def chat(
        self, response_model: Type[T], messages: list, max_retries: int = 3, **kwargs
    ) -> T:
        """
        Multi-turn chat with structured outputs

        Args:
            response_model: Pydantic model for response structure
            messages: List of message dicts with 'role' and 'content'
            max_retries: Retry attempts for validation failures
            **kwargs: Additional Gemini arguments

        Returns:
            Instance of response_model with validated data
        """
        try:
            response = self.client.create(
                response_model=response_model,
                messages=messages,
                max_retries=max_retries,
                **kwargs,
            )

            return response

        except Exception as e:
            self.logger.error(f"Error in chat completion: {str(e)}", exc_info=True)
            raise

    def generate_streaming(
        self,
        prompt: str,
        image_data: Optional[str] = None,
        **kwargs,
    ) -> Generator[str, None, None]:
        """
        Generate streaming response from Gemini.

        Yields text chunks as they arrive from the API.
        Useful for real-time UI updates.

        Args:
            prompt: The prompt text
            image_data: Optional base64 encoded image
            **kwargs: Additional arguments passed to Gemini

        Yields:
            Text chunks as they arrive

        Note:
            This bypasses Instructor validation for streaming.
            Use for real-time display, then validate complete response.
        """
        try:
            # Prepare content
            content = [prompt]

            # Add image if provided
            if image_data:
                content.append({"mime_type": "image/jpeg", "data": image_data})

            # Generate with streaming
            response = self.model.generate_content(content, stream=True, **kwargs)

            # Yield chunks as they arrive
            for chunk in response:
                if chunk.text:
                    yield chunk.text
            usage_meter.add_response(self.model_name, response)

        except Exception as e:
            self.logger.error(
                f"Error in streaming generation: {str(e)}", exc_info=True
            )
            raise

    def generate_streaming_json(
        self,
        response_model: Type[T],
        prompt: str,
        image_data: Optional[str] = None,
        **kwargs,
    ) -> Generator[Dict[str, Any], None, T]:
        """
        Generate streaming response with final validation.

        Streams raw text chunks, then validates complete response
        against the Pydantic model at the end.

        Args:
            response_model: Pydantic model for final validation
            prompt: The prompt text
            image_data: Optional base64 encoded image
            **kwargs: Additional Gemini arguments

        Yields:
            Dict with 'type' and 'data':
            - {"type": "chunk", "data": "text chunk"}
            - {"type": "complete", "data": validated_model}
            - {"type": "error", "data": error_message}

        Returns:
            The final validated model instance
        """
        accumulated_text = ""

        try:
            # Prepare content with JSON instruction
            json_prompt = f"""{prompt}

IMPORTANT: Return your response as valid JSON that matches the required schema.
Do not include markdown code blocks or any other formatting."""

            content = [json_prompt]
            if image_data:
                content.append({"mime_type": "image/jpeg", "data": image_data})

            # Generate with streaming
            response = self.model.generate_content(content, stream=True, **kwargs)

            # Yield chunks as they arrive
            for chunk in response:
                if chunk.text:
                    accumulated_text += chunk.text
                    yield {"type": "chunk", "data": chunk.text}
            usage_meter.add_response(self.model_name, response)

            # Parse and validate complete response
            try:
                # Clean up JSON (remove markdown if present)
                json_text = accumulated_text.strip()
                if json_text.startswith("```"):
                    json_text = json_text.split("```")[1]
                    if json_text.startswith("json"):
                        json_text = json_text[4:]
                    json_text = json_text.strip()

                # Parse JSON
                parsed_data = json.loads(json_text)

                # Validate with Pydantic
                validated = response_model.model_validate(parsed_data)

                yield {"type": "complete", "data": validated}
                return validated

            except json.JSONDecodeError as e:
                self.logger.error(f"JSON parse error: {e}")
                yield {"type": "error", "data": f"JSON parse error: {str(e)}"}
                raise

            except Exception as e:
                self.logger.error(f"Validation error: {e}")
                yield {"type": "error", "data": f"Validation error: {str(e)}"}
                raise

        except Exception as e:
            self.logger.error(
                f"Error in streaming JSON generation: {str(e)}", exc_info=True
            )
            yield {"type": "error", "data": str(e)}
            raise


class GeminiClientFactory:
    """
    Factory for creating Gemini clients with different models
    Singleton pattern to reuse clients
    """

    _clients: Dict[str, GeminiInstructorClient] = {}

    @classmethod
    def get_client(
        cls, model_name: str = "gemini-2.5-flash", api_key: Optional[str] = None
    ) -> GeminiInstructorClient:
        """
        Get or create a Gemini client for the specified model

        Args:
            model_name: Gemini model name
            api_key: Optional API key

        Returns:
            GeminiInstructorClient instance
        """
        # Create cache key
        cache_key = f"{model_name}:{api_key or 'default'}"

        # Return cached client if exists
        if cache_key in cls._clients:
            return cls._clients[cache_key]

        # Create new client
        client = GeminiInstructorClient(model_name=model_name, api_key=api_key)

        # Cache and return
        cls._clients[cache_key] = client
        return client

    @classmethod
    def clear_cache(cls):
        """Clear all cached clients"""
        cls._clients.clear()
