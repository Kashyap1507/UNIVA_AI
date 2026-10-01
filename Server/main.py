"""
Whizkids Server - Main Application Entry Point
"""

import os

from flask import Flask, send_from_directory

from app import create_app
from app.config import config

# Get config based on FLASK_ENV
env = os.environ.get("FLASK_ENV", "development")
app = create_app(config.get(env, config["development"]))

if __name__ == "__main__":
    # Development server
    port = int(os.environ.get("PORT", 5000))
    debug = os.environ.get("FLASK_ENV") == "development"

    # Note: File serving routes are handled in app/__init__.py
    # These duplicate routes have been removed to prevent path traversal vulnerabilities

    app.run(host="0.0.0.0", port=port, debug=debug)
