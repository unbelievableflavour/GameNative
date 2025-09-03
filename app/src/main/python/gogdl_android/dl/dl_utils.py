"""
Android-compatible download utilities
"""

import json
import logging
import requests
import zlib
from typing import Dict, Any, Tuple
from gogdl_android import constants

logger = logging.getLogger("DLUtils")

def get_json(api_handler, url: str) -> Dict[str, Any]:
    """Get JSON data from URL using authenticated request"""
    try:
        response = api_handler.get_authenticated_request(url)
        response.raise_for_status()
        return response.json()
    except Exception as e:
        logger.error(f"Failed to get JSON from {url}: {e}")
        raise

def get_zlib_encoded(api_handler, url: str) -> Tuple[Dict[str, Any], Dict[str, str]]:
    """Get and decompress zlib-encoded data from URL"""
    try:
        response = api_handler.get_authenticated_request(url)
        response.raise_for_status()
        
        # Decompress zlib data
        compressed_data = response.content
        decompressed_data = zlib.decompress(compressed_data)
        
        # Parse as JSON
        json_data = json.loads(decompressed_data.decode('utf-8'))
        
        return json_data, dict(response.headers)
    except Exception as e:
        logger.error(f"Failed to get zlib data from {url}: {e}")
        raise

def download_file_chunk(url: str, start: int, end: int, headers: Dict[str, str] = None) -> bytes:
    """Download a specific chunk of a file using Range headers"""
    try:
        chunk_headers = headers.copy() if headers else {}
        chunk_headers['Range'] = f'bytes={start}-{end}'
        
        response = requests.get(
            url, 
            headers=chunk_headers,
            timeout=(constants.CONNECTION_TIMEOUT, constants.READ_TIMEOUT),
            stream=True
        )
        response.raise_for_status()
        
        return response.content
    except Exception as e:
        logger.error(f"Failed to download chunk {start}-{end} from {url}: {e}")
        raise


def galaxy_path(manifest_hash: str):
    """Format chunk hash for GOG Galaxy path structure"""
    if manifest_hash.find("/") == -1:
        return f"{manifest_hash[0:2]}/{manifest_hash[2:4]}/{manifest_hash}"
    return manifest_hash


def merge_url_with_params(url_template: str, parameters: dict):
    """Replace parameters in URL template"""
    result_url = url_template
    for key, value in parameters.items():
        result_url = result_url.replace("{" + key + "}", str(value))
    return result_url


def get_secure_link(api_handler, path: str, game_id: str, generation: int = 2, root: str = None, logger=None):
    """Get secure download links from GOG API - this is the key to proper chunk authentication"""
    import time
    from typing import List
    
    url = ""
    if generation == 2:
        url = f"{constants.GOG_CONTENT_SYSTEM}/products/{game_id}/secure_link?_version=2&generation=2&path={path}"
    elif generation == 1:
        url = f"{constants.GOG_CONTENT_SYSTEM}/products/{game_id}/secure_link?_version=2&type=depot&path={path}"
    
    if root:
        url += f"&root={root}"
    
    try:
        response = api_handler.get_authenticated_request(url)
        
        if response.status_code != 200:
            logger.warning(f"Invalid secure link response: {response.status_code}")
            time.sleep(0.2)
            return get_secure_link(api_handler, path, game_id, generation, root)
        
        js = response.json()
        return js.get('urls', [])
        
    except Exception as e:
        logger.error(f"Failed to get secure link: {e}")
        time.sleep(0.2)
        return get_secure_link(api_handler, path, game_id, generation, root)

