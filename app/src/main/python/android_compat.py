"""
Android compatibility module for GOGDL
Provides fallbacks for modules not available in Chaquopy
"""

import sys
import os

# Mock _posixshmem module if not available
try:
    import _posixshmem
except ImportError:
    print("Warning: _posixshmem not available on Android, creating mock")
    
    class MockPosixShmem:
        @staticmethod
        def shm_open(name, flags, mode=0o600):
            # Mock shared memory open - return a dummy file descriptor
            return -1
        
        @staticmethod
        def shm_unlink(name):
            # Mock shared memory unlink - do nothing
            pass
        
        def __getattr__(self, name):
            # For any other attributes, return a no-op function
            def noop(*args, **kwargs):
                pass
            return noop
    
    sys.modules['_posixshmem'] = MockPosixShmem()

# Mock multiprocessing.shared_memory if needed
try:
    import multiprocessing.shared_memory
except ImportError:
    print("Warning: multiprocessing.shared_memory not available, creating mock")
    
    import multiprocessing
    
    class MockSharedMemory:
        def __init__(self, name=None, create=False, size=0):
            # Mock shared memory - just store basic attributes
            self.name = name or f"mock_shm_{id(self)}"
            self.size = size
            self.buf = bytearray(size) if size > 0 else bytearray()
        
        def close(self):
            # Mock close - do nothing
            pass
        
        def unlink(self):
            # Mock unlink - do nothing
            pass
    
    class MockSharedMemoryModule:
        SharedMemory = MockSharedMemory
    
    multiprocessing.shared_memory = MockSharedMemoryModule()

# Patch multiprocessing to use single process on Android
original_cpu_count = None
try:
    from multiprocessing import cpu_count as original_cpu_count
except ImportError:
    pass

def android_cpu_count():
    """Return 1 CPU for Android to avoid multiprocessing issues"""
    return 1

# Replace cpu_count to avoid multiprocessing
if original_cpu_count:
    import multiprocessing
    multiprocessing.cpu_count = android_cpu_count

# Patch other potential multiprocessing issues
try:
    import multiprocessing
    
    # Mock Process class to avoid forking issues on Android
    class MockProcess:
        def __init__(self, *args, **kwargs):
            self.args = args
            self.kwargs = kwargs
            self._started = False
        
        def start(self):
            self._started = True
            # Don't actually start a process on Android
            pass
        
        def join(self, timeout=None):
            # Mock join - do nothing
            pass
        
        def is_alive(self):
            return False
        
        def terminate(self):
            pass
    
    # Replace Process class if it exists
    if hasattr(multiprocessing, 'Process'):
        multiprocessing.Process = MockProcess
        
except ImportError:
    pass

print("Android compatibility patches applied for GOGDL")
