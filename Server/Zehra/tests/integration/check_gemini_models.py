#!/usr/bin/env python3
"""
Test script to check available Gemini models with current API key
"""

import os

import google.generativeai as genai
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Get API key
api_key = os.getenv("GOOGLE_GEMINI_API_KEY")
if not api_key:
    print("ERROR: GOOGLE_GEMINI_API_KEY not found in environment")
    exit(1)

print(f"Testing API key: {api_key[:10]}...")

# Configure Gemini
genai.configure(api_key=api_key)

print("\nAvailable models:")
try:
    models = genai.list_models()
    for model in models:
        print(f"- {model.name}")
        print(f"  Display name: {model.display_name}")
        print(f"  Supported methods: {model.supported_generation_methods}")
        print(
            f"  Vision: {'yes' if 'generateContent' in model.supported_generation_methods else 'no'}"
        )
        print()
except Exception as e:
    print(f"Error listing models: {e}")

# Test basic model initialization
test_models = [
    "gemini-pro",
    "gemini-pro-vision",
    "gemini-1.5-pro",
    "gemini-1.5-flash",
    "models/gemini-pro",
    "models/gemini-pro-vision",
    "models/gemini-1.5-pro",
    "models/gemini-1.5-flash",
]

print("\nTesting model initialization:")
for model_name in test_models:
    try:
        model = genai.GenerativeModel(model_name)
        print(f"✅ {model_name} - OK")
    except Exception as e:
        print(f"❌ {model_name} - Error: {e}")
