"""
Android-compatible authentication module
"""

import json
import os
import logging
from typing import Optional, Dict, Any

class AuthorizationManager:
    """Android-compatible authorization manager"""
    
    def __init__(self, config_path: str):
        self.config_path = config_path
        self.logger = logging.getLogger("AUTH")
        self.credentials_data = {}
        self._read_config()
        
    def _read_config(self):
        """Read credentials from config file"""
        if os.path.exists(self.config_path):
            try:
                with open(self.config_path, "r") as f:
                    self.credentials_data = json.load(f)
            except Exception as e:
                self.logger.error(f"Failed to read config: {e}")
                self.credentials_data = {}
    
    def get_credentials(self, client_id=None, client_secret=None):
        """
        Reads data from config and returns it
        :param client_id: GOG client ID
        :return: dict with credentials or None if not present
        """
        if not client_id:
            client_id = "46899977096215655"  # Default GOG client ID
            
        if client_id in self.credentials_data:
            return self.credentials_data[client_id]
        
        # Fallback: look for any credentials in the file
        for key, value in self.credentials_data.items():
            if isinstance(value, dict) and 'access_token' in value:
                return value
                
        return None
        
    def get_access_token(self) -> Optional[str]:
        """Get access token from auth config"""
        credentials = self.get_credentials()
        if credentials and 'access_token' in credentials:
            return credentials['access_token']
        return None
            
    def is_authenticated(self) -> bool:
        """Check if user is authenticated"""
        return self.get_access_token() is not None
