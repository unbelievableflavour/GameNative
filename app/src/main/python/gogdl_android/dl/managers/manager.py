"""
Android-compatible download manager
Replaces multiprocessing with threading for Android compatibility
"""

from dataclasses import dataclass
import os
import logging
import json
import threading
from concurrent.futures import ThreadPoolExecutor

from gogdl_android import constants
from gogdl_android.dl.managers import linux, v2

@dataclass
class UnsupportedPlatform(Exception):
    pass

class AndroidManager:
    """Android-compatible version of GOGDL Manager that uses threading instead of multiprocessing"""
    
    def __init__(self, arguments, unknown_arguments, api_handler):
        self.arguments = arguments
        self.unknown_arguments = unknown_arguments
        self.api_handler = api_handler

        self.platform = arguments.platform
        self.should_append_folder_name = self.arguments.command == "download"
        self.is_verifying = self.arguments.command == "repair"
        self.game_id = arguments.id
        self.branch = arguments.branch or None
        
        # Use a reasonable number of threads for Android
        if hasattr(arguments, "workers_count"):
            self.allowed_threads = min(int(arguments.workers_count), 4)  # Limit threads on mobile
        else:
            self.allowed_threads = 2  # Conservative default for Android

        self.logger = logging.getLogger("AndroidManager")

    def download(self):
        """Download game using Android-compatible threading"""
        try:
            self.logger.info(f"Starting Android download for game {self.game_id}")
            
            if self.platform == "windows":
                # Use the existing v2 manager but with threading modifications
                manager = v2.V2Manager(
                    self.arguments, 
                    self.unknown_arguments, 
                    self.api_handler,
                    max_workers=self.allowed_threads
                )
                manager.download()
            elif self.platform == "linux":
                # Use Linux manager with threading
                manager = linux.LinuxManager(
                    self.arguments,
                    self.unknown_arguments, 
                    self.api_handler,
                    max_workers=self.allowed_threads
                )
                manager.download()
            else:
                raise UnsupportedPlatform(f"Platform {self.platform} not supported on Android")
                
        except Exception as e:
            self.logger.error(f"Download failed: {e}")
            raise

    def info(self):
        """Get game info"""
        try:
            # Use existing info logic but Android-compatible
            if self.platform == "windows":
                manager = v2.V2Manager(self.arguments, self.unknown_arguments, self.api_handler)
                manager.info()
            else:
                raise UnsupportedPlatform(f"Info for platform {self.platform} not supported")
        except Exception as e:
            self.logger.error(f"Info failed: {e}")
            raise
