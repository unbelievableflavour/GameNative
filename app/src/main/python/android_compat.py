"""
Android compatibility module for GOGDL
Provides fallbacks for modules not available in Chaquopy
"""

import sys
import os

# Disable Android fdsan to prevent file descriptor crashes
print("Warning: Disabling Android fdsan to prevent file descriptor conflicts")
try:
    import ctypes
    # Try to disable fdsan by setting the environment variable
    os.environ['ANDROID_FDSAN_DISABLED'] = '1'
    
    # Also try to call android_fdsan_set_error_level if available
    try:
        libc = ctypes.CDLL("libc.so")
        # Set fdsan error level to 0 (disabled)
        if hasattr(libc, 'android_fdsan_set_error_level'):
            libc.android_fdsan_set_error_level(0)
            print("Successfully disabled fdsan via android_fdsan_set_error_level")
    except Exception as e:
        print(f"Could not disable fdsan via libc call: {e}")
        
except Exception as e:
    print(f"Could not disable fdsan: {e}")

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
    
    class MockShareableList:
        def __init__(self, sequence=None):
            self._data = list(sequence) if sequence else []
        
        def __getitem__(self, index):
            return self._data[index]
        
        def __setitem__(self, index, value):
            self._data[index] = value
        
        def __len__(self):
            return len(self._data)
        
        def __iter__(self):
            return iter(self._data)
    
    # Create mock module
    class MockSharedMemoryModule:
        SharedMemory = MockSharedMemory
        ShareableList = MockShareableList
    
    multiprocessing.shared_memory = MockSharedMemoryModule()
    sys.modules['multiprocessing.shared_memory'] = MockSharedMemoryModule()

# Aggressively patch multiprocessing to avoid semaphore issues
print("Warning: Patching multiprocessing to avoid semaphore issues on Android")

# Mock the entire multiprocessing module early to prevent semaphore initialization
try:
    import multiprocessing
    
    # Store original functions we might need
    original_cpu_count = getattr(multiprocessing, 'cpu_count', lambda: 1)
    
    # Mock all synchronization primitives
    class MockLock:
        def __init__(self):
            pass
        def acquire(self, blocking=True, timeout=None):
            return True
        def release(self):
            pass
        def __enter__(self):
            return self
        def __exit__(self, exc_type, exc_val, exc_tb):
            pass
    
    class MockSemaphore:
        def __init__(self, value=1):
            self._value = value
        def acquire(self, blocking=True, timeout=None):
            return True
        def release(self):
            pass
        def __enter__(self):
            return self
        def __exit__(self, exc_type, exc_val, exc_tb):
            pass
    
    class MockEvent:
        def __init__(self):
            self._set = False
        def set(self):
            self._set = True
        def clear(self):
            self._set = False
        def is_set(self):
            return self._set
        def wait(self, timeout=None):
            return self._set
    
    class MockQueue:
        def __init__(self, maxsize=0):
            self._queue = []
            self._maxsize = maxsize
        def put(self, item, block=True, timeout=None):
            self._queue.append(item)
        def get(self, block=True, timeout=None):
            if self._queue:
                return self._queue.pop(0)
            return None
        def empty(self):
            return len(self._queue) == 0
        def qsize(self):
            return len(self._queue)
    
    class MockProcess:
        def __init__(self, target=None, args=(), kwargs=None):
            self.target = target
            self.args = args
            self.kwargs = kwargs or {}
            self._started = False
        def start(self):
            self._started = True
            # Execute target function directly instead of forking
            if self.target:
                try:
                    self.target(*self.args, **self.kwargs)
                except Exception:
                    pass
        def join(self, timeout=None):
            pass
        def is_alive(self):
            return False
        def terminate(self):
            pass
        def kill(self):
            pass
    
    class MockPool:
        def __init__(self, processes=None, *args, **kwargs):
            self.processes = processes or 1
        def map(self, func, iterable, chunksize=None):
            return list(map(func, iterable))
        def apply(self, func, args=(), kwds=None):
            return func(*args, **(kwds or {}))
        def apply_async(self, func, args=(), kwds=None, callback=None):
            result = func(*args, **(kwds or {}))
            if callback:
                callback(result)
            return MockAsyncResult(result)
        def close(self):
            pass
        def join(self):
            pass
        def terminate(self):
            pass
        def __enter__(self):
            return self
        def __exit__(self, exc_type, exc_val, exc_tb):
            self.close()
            self.join()
    
    class MockAsyncResult:
        def __init__(self, result):
            self._result = result
        def get(self, timeout=None):
            return self._result
        def ready(self):
            return True
        def successful(self):
            return True
    
    # Replace all multiprocessing classes
    multiprocessing.Lock = MockLock
    multiprocessing.RLock = MockLock
    multiprocessing.Semaphore = MockSemaphore
    multiprocessing.BoundedSemaphore = MockSemaphore
    multiprocessing.Event = MockEvent
    multiprocessing.Queue = MockQueue
    multiprocessing.Process = MockProcess
    multiprocessing.Pool = MockPool
    multiprocessing.cpu_count = lambda: 1
    
    # Also patch the synchronize module if it exists
    try:
        import multiprocessing.synchronize
        multiprocessing.synchronize.Lock = MockLock
        multiprocessing.synchronize.RLock = MockLock
        multiprocessing.synchronize.Semaphore = MockSemaphore
        multiprocessing.synchronize.BoundedSemaphore = MockSemaphore
        multiprocessing.synchronize.Event = MockEvent
    except ImportError:
        pass
    
    # Patch context module if it exists
    try:
        import multiprocessing.context
        class MockContext:
            Lock = MockLock
            RLock = MockLock
            Semaphore = MockSemaphore
            BoundedSemaphore = MockSemaphore
            Event = MockEvent
            Queue = MockQueue
            Process = MockProcess
            Pool = MockPool
            def cpu_count(self):
                return 1
        
        multiprocessing.context.DefaultContext = MockContext
        if hasattr(multiprocessing, 'get_context'):
            multiprocessing.get_context = lambda method=None: MockContext()
    except ImportError:
        pass
    
except ImportError:
    pass

# Mock POSIX semaphores (sem_open) which are not available on Android
try:
    import _posixsubprocess
    # Try to use sem_open to see if it's available
    import os
    if hasattr(os, 'sem_open'):
        # Test if sem_open actually works
        try:
            sem = os.sem_open('/test_sem', os.O_CREAT | os.O_EXCL, 0o600, 1)
            os.sem_close(sem)
            os.sem_unlink('/test_sem')
        except (OSError, AttributeError):
            # sem_open doesn't work, need to mock it
            raise ImportError("sem_open not functional")
except (ImportError, OSError, AttributeError):
    print("Warning: POSIX semaphores not available on Android, creating mock")
    
    # Mock semaphore functions
    class MockSemaphore:
        def __init__(self, value=1):
            self._value = value
            self._mock_fd = -1
        
        def acquire(self, blocking=True, timeout=None):
            return True
        
        def release(self):
            pass
        
        def close(self):
            pass
        
        def unlink(self):
            pass
    
    # Mock the os module semaphore functions
    import os
    
    def mock_sem_open(name, flags, mode=0o600, value=1):
        return MockSemaphore(value)
    
    def mock_sem_close(sem):
        pass
    
    def mock_sem_unlink(name):
        pass
    
    # Replace os functions if they exist
    os.sem_open = mock_sem_open
    os.sem_close = mock_sem_close
    os.sem_unlink = mock_sem_unlink

# Patch multiprocessing.resource_tracker to prevent subprocess spawning
try:
    import multiprocessing.resource_tracker
    
    class MockResourceTracker:
        def __init__(self):
            pass
        
        def register(self, name, rtype):
            # Mock register - do nothing
            pass
        
        def unregister(self, name, rtype):
            # Mock unregister - do nothing
            pass
        
        def ensure_running(self):
            # Mock ensure_running - do nothing, don't spawn subprocess
            pass
        
        def _cleanup(self):
            # Mock cleanup - do nothing
            pass
    
    # Replace the resource tracker
    mock_tracker = MockResourceTracker()
    multiprocessing.resource_tracker._resource_tracker = mock_tracker
    multiprocessing.resource_tracker.register = mock_tracker.register
    multiprocessing.resource_tracker.unregister = mock_tracker.unregister
    
    # Also patch the main function that was trying to run
    def mock_main(fd):
        # Mock main function - do nothing
        pass
    
    multiprocessing.resource_tracker.main = mock_main
    
except ImportError:
    pass

# Patch additional potential file descriptor issues
try:
    import os
    import io
    
    # Store original functions
    original_pipe = getattr(os, 'pipe', None)
    original_close = getattr(os, 'close', None)
    
    def mock_pipe():
        # Return mock file descriptors that won't cause issues
        return (1, 2)  # Return stdout and stderr fds
    
    def safe_close(fd):
        # Only close if it's a real file descriptor and not our mocks
        if original_close and isinstance(fd, int) and fd > 2:
            try:
                return original_close(fd)
            except (OSError, ValueError):
                # Ignore close errors on Android
                pass
    
    # Replace problematic functions
    if original_pipe:
        os.pipe = mock_pipe
    if original_close:
        os.close = safe_close
        
    # Also patch subprocess to prevent any remaining subprocess calls
    try:
        import subprocess
        
        class MockPopen:
            def __init__(self, *args, **kwargs):
                self.returncode = 0
                self.stdout = io.StringIO("")
                self.stderr = io.StringIO("")
                self.stdin = io.StringIO("")
            
            def communicate(self, input=None, timeout=None):
                return ("", "")
            
            def wait(self, timeout=None):
                return 0
            
            def poll(self):
                return 0
            
            def terminate(self):
                pass
            
            def kill(self):
                pass
        
        subprocess.Popen = MockPopen
        subprocess.call = lambda *args, **kwargs: 0
        subprocess.check_call = lambda *args, **kwargs: 0
        subprocess.check_output = lambda *args, **kwargs: b""
        subprocess.run = lambda *args, **kwargs: subprocess.CompletedProcess(args, 0, b"", b"")
        
    except ImportError:
        pass
        
except ImportError:
    pass

# Patch stdin to avoid EOF issues
try:
    import sys
    import io
    
    # Create a comprehensive mock stdin that handles all input scenarios
    class MockStdin:
        def __init__(self):
            self._buffer = io.StringIO("")
        
        def read(self, size=-1):
            return ""
        
        def readline(self, size=-1):
            return ""
        
        def readlines(self, hint=-1):
            return []
        
        def __iter__(self):
            return iter([])
        
        def __next__(self):
            raise StopIteration
        
        def close(self):
            pass
        
        def flush(self):
            pass
        
        def isatty(self):
            return False
        
        def readable(self):
            return True
        
        def seekable(self):
            return False
        
        def writable(self):
            return False
        
        def fileno(self):
            return 0  # Return stdin file descriptor
        
        @property
        def closed(self):
            return False
        
        @property
        def encoding(self):
            return 'utf-8'
        
        @property
        def errors(self):
            return 'strict'
        
        @property
        def newlines(self):
            return None
    
    # Replace sys.stdin with our mock
    original_stdin = sys.stdin
    sys.stdin = MockStdin()
    print("Warning: Replaced stdin with mock to prevent EOF errors")
    
except Exception as e:
    print(f"Could not patch stdin: {e}")

# Patch socket and urllib to handle network issues gracefully
try:
    import socket
    import urllib.request
    import urllib.error
    
    # Store original socket methods
    original_socket_recv = socket.socket.recv
    original_socket_send = socket.socket.send
    original_urlopen = urllib.request.urlopen
    
    def patched_socket_recv(self, bufsize, flags=0):
        try:
            return original_socket_recv(self, bufsize, flags)
        except (ConnectionResetError, BrokenPipeError, EOFError) as e:
            print(f"Warning: Socket recv error handled: {e}")
            return b''  # Return empty bytes instead of raising
        except Exception as e:
            print(f"Warning: Unexpected socket recv error: {e}")
            raise
    
    def patched_socket_send(self, data, flags=0):
        try:
            return original_socket_send(self, data, flags)
        except (ConnectionResetError, BrokenPipeError, EOFError) as e:
            print(f"Warning: Socket send error handled: {e}")
            return 0  # Return 0 bytes sent
        except Exception as e:
            print(f"Warning: Unexpected socket send error: {e}")
            raise
    
    def patched_urlopen(*args, **kwargs):
        try:
            return original_urlopen(*args, **kwargs)
        except (urllib.error.URLError, EOFError, ConnectionResetError) as e:
            print(f"Warning: URL open error handled: {e}")
            # Re-raise after logging - let GOGDL handle retry logic
            raise
        except Exception as e:
            print(f"Warning: Unexpected URL open error: {e}")
            raise
    
    # Apply patches
    socket.socket.recv = patched_socket_recv
    socket.socket.send = patched_socket_send
    urllib.request.urlopen = patched_urlopen
    
    print("Warning: Applied network I/O patches for Android compatibility")
    
except Exception as e:
    print(f"Could not patch network I/O: {e}")

# Patch file operations to handle Android filesystem issues
try:
    import builtins
    
    # Store original open function
    original_open = builtins.open
    
    def patched_open(file, mode='r', buffering=-1, encoding=None, errors=None, newline=None, closefd=True, opener=None):
        try:
            return original_open(file, mode, buffering, encoding, errors, newline, closefd, opener)
        except (EOFError, OSError) as e:
            print(f"Warning: File open error handled for {file}: {e}")
            # For read operations that fail with EOF, return empty file-like object
            if 'r' in mode:
                import io
                return io.StringIO("") if 'b' not in mode else io.BytesIO(b"")
            # For write operations, re-raise
            raise
        except Exception as e:
            print(f"Warning: Unexpected file open error for {file}: {e}")
            raise
    
    # Apply patch
    builtins.open = patched_open
    
    print("Warning: Applied file I/O patches for Android compatibility")
    
except Exception as e:
    print(f"Could not patch file I/O: {e}")

print("Android compatibility patches applied for GOGDL")
