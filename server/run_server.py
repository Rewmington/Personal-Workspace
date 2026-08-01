from __future__ import annotations

import socket
import sys
from pathlib import Path

import uvicorn

# Double-clicking a .py file does not guarantee that the server directory is
# the current working directory. Put it on sys.path before importing app.
SERVER_DIR = Path(__file__).resolve().parent
if str(SERVER_DIR) not in sys.path:
    sys.path.insert(0, str(SERVER_DIR))

from app.config import settings
from app.main import app


def local_ip() -> str:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        return sock.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        sock.close()


def wait_after_error() -> None:
    try:
        input("\n启动失败，按 Enter 键关闭窗口...")
    except EOFError:
        pass


def main() -> int:
    print(f"个人工作台服务启动: http://{local_ip()}:{settings.port}")
    print("健康检查: /api/health，接口文档: /docs，WebSocket: /ws")
    try:
        uvicorn.run(app, host=settings.host, port=settings.port, reload=False, access_log=False)
    except OSError as exc:
        print(f"启动失败：无法监听端口 {settings.port}。\n{exc}")
        print("请关闭已运行的服务，或设置 WORKSTATION_PORT 使用其他端口。")
        wait_after_error()
        return 1
    except SystemExit as exc:
        code = exc.code if isinstance(exc.code, int) else 1
        if code != 0:
            print(f"启动失败：端口 {settings.port} 可能已被其他程序占用。")
            print("请关闭已运行的服务，或设置 WORKSTATION_PORT 使用其他端口。")
            wait_after_error()
        return code
    except KeyboardInterrupt:
        print("\n服务已停止。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
