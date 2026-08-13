#!/usr/bin/env python3
"""
WebSocket → TCP Bridge for QQ Farm
Bridges browser WebSocket connections to the Java server on port 5050.

Usage:
  pip install websockets
  python websocket_proxy.py

Then open docs/qq-farm.html in your browser (or via local server).
The page connects to ws://localhost:8765 which forwards to 127.0.0.1:5050.
"""

import asyncio
import websockets

BRIDGE_PORT = 8765
SERVER_HOST = "127.0.0.1"
SERVER_PORT = 5050


async def handle_client(websocket):
    """Handle a single WebSocket client, bridging to the TCP server."""
    tcp_reader = None
    tcp_writer = None
    try:
        # Connect to the Java server
        tcp_reader, tcp_writer = await asyncio.open_connection(SERVER_HOST, SERVER_PORT)
        print(f"[BRIDGE] Client {websocket.remote_address} connected → forwarding to {SERVER_HOST}:{SERVER_PORT}")

        async def ws_to_tcp():
            """Forward WebSocket messages → TCP server."""
            try:
                async for message in websocket:
                    tcp_writer.write(message.encode('utf-8') + b'\n')
                    await tcp_writer.drain()
            except websockets.exceptions.ConnectionClosed:
                pass

        async def tcp_to_ws():
            """Forward TCP server responses → WebSocket."""
            try:
                while True:
                    data = await tcp_reader.read(4096)
                    if not data:
                        break
                    await websocket.send(data.decode('utf-8', errors='replace'))
            except websockets.exceptions.ConnectionClosed:
                pass
            except Exception:
                pass

        # Run both directions concurrently
        done, pending = await asyncio.wait(
            [asyncio.create_task(ws_to_tcp()), asyncio.create_task(tcp_to_ws())],
            return_when=asyncio.FIRST_COMPLETED,
        )
        for task in pending:
            task.cancel()

    except ConnectionRefusedError:
        await websocket.send("ERR Cannot connect to Java server. Make sure Server is running on port 5050.")
        print(f"[BRIDGE] Java server not reachable at {SERVER_HOST}:{SERVER_PORT}")
    except Exception as e:
        print(f"[BRIDGE] Error: {e}")
    finally:
        if tcp_writer:
            try:
                tcp_writer.close()
                await tcp_writer.wait_closed()
            except Exception:
                pass
        print(f"[BRIDGE] Client {websocket.remote_address} disconnected")


async def main():
    print("=" * 50)
    print(f"QQ Farm WebSocket → TCP Bridge")
    print(f"  Browser connects to: ws://localhost:{BRIDGE_PORT}")
    print(f"  Forwards to:         {SERVER_HOST}:{SERVER_PORT}")
    print("=" * 50)
    async with websockets.serve(handle_client, "localhost", BRIDGE_PORT):
        print(f"[BRIDGE] Listening on ws://localhost:{BRIDGE_PORT} — press Ctrl+C to stop")
        await asyncio.Future()  # run forever


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[BRIDGE] Shutting down.")
