"""Windows 系统剪贴板读写（ctypes，零第三方依赖）。"""

from __future__ import annotations

import ctypes
from ctypes import wintypes

CF_UNICODETEXT = 13
GMEM_MOVEABLE = 0x0002
GMEM_ZEROINIT = 0x0040

_user32 = ctypes.windll.user32
_kernel32 = ctypes.windll.kernel32

# 显式声明 Win32 函数签名，避免 64 位句柄被 c_int 截断
_user32.OpenClipboard.argtypes = [wintypes.HWND]
_user32.OpenClipboard.restype = wintypes.BOOL
_user32.EmptyClipboard.restype = wintypes.BOOL
_user32.IsClipboardFormatAvailable.argtypes = [wintypes.UINT]
_user32.IsClipboardFormatAvailable.restype = wintypes.BOOL
_user32.GetClipboardData.argtypes = [wintypes.UINT]
_user32.GetClipboardData.restype = wintypes.HANDLE
_user32.SetClipboardData.argtypes = [wintypes.UINT, wintypes.HANDLE]
_user32.SetClipboardData.restype = wintypes.HANDLE
_user32.CloseClipboard.restype = wintypes.BOOL

_kernel32.GlobalAlloc.argtypes = [wintypes.UINT, ctypes.c_size_t]
_kernel32.GlobalAlloc.restype = wintypes.HANDLE
_kernel32.GlobalLock.argtypes = [wintypes.HANDLE]
_kernel32.GlobalLock.restype = wintypes.LPVOID
_kernel32.GlobalUnlock.argtypes = [wintypes.HANDLE]
_kernel32.GlobalUnlock.restype = wintypes.BOOL


def get_clipboard_text() -> str | None:
    """读取系统剪贴板文本；无文本 / 打开失败时返回 None。"""
    if not _user32.OpenClipboard(None):
        return None
    try:
        if not _user32.IsClipboardFormatAvailable(CF_UNICODETEXT):
            return None
        handle = _user32.GetClipboardData(CF_UNICODETEXT)
        if not handle:
            return None
        ptr = _kernel32.GlobalLock(handle)
        if not ptr:
            return None
        try:
            return ctypes.wstring_at(ptr).rstrip("\x00") or None
        finally:
            _kernel32.GlobalUnlock(handle)
    finally:
        _user32.CloseClipboard()


def set_clipboard_text(text: str) -> bool:
    """写入系统剪贴板文本；失败返回 False。系统接管内存，不手动释放。"""
    if not _user32.OpenClipboard(None):
        return False
    try:
        _user32.EmptyClipboard()
        data = (text + "\x00").encode("utf-16-le")
        handle = _kernel32.GlobalAlloc(GMEM_MOVEABLE | GMEM_ZEROINIT, len(data))
        if not handle:
            return False
        ptr = _kernel32.GlobalLock(handle)
        if not ptr:
            return False
        ctypes.memmove(ptr, data, len(data))
        _kernel32.GlobalUnlock(handle)
        _user32.SetClipboardData(CF_UNICODETEXT, handle)
        return True
    except Exception:
        return False
    finally:
        try:
            _user32.CloseClipboard()
        except Exception:
            pass