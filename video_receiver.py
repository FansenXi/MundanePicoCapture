#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Video Frame Receiver for CaptureSDKDemo

This script receives HEVC encoded video frames from Android device via TCP
and saves them to a single h265 video file. It listens on port 12345 by default.
"""

import socket
import struct
import time
import os

class VideoReceiver:
    def __init__(self, host='0.0.0.0', port=12345, save_dir='received_frames'):
        """
        Initialize video receiver
        
        Args:
            host: Host to listen on
            port: Port to listen on
            save_dir: Directory to save the video file
        """
        self.host = host
        self.port = port
        self.save_dir = save_dir
        self.socket = None
        self.client_socket = None
        self.running = False
        
        # Create save directory if it doesn't exist
        os.makedirs(self.save_dir, exist_ok=True)
        
    def start(self):
        """Start the video receiver"""
        try:
            # Get and display local IP address
            print(f"=== Video Receiver ===")
            print(f"====================")
            
            # Create TCP socket
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            
            # Bind and listen
            self.socket.bind((self.host, self.port))
            self.socket.listen(1)
            
            print(f"Video Receiver started. Listening on {self.host}:{self.port}")
            print(f"Received video will be saved to: {self.save_dir}")
            
            self.running = True
            
            while self.running:
                print("Waiting for connection...")
                self.client_socket, addr = self.socket.accept()
                print(f"Connected to {addr}")
                
                # Set socket timeout to prevent hanging
                self.client_socket.settimeout(5.0)
                
                try:
                    # Try to receive format info, but don't block indefinitely
                    # The format info might not always be sent first, especially after reconnections
                    format_info = b''
                    try:
                        # Set a short timeout for format info reception
                        self.client_socket.settimeout(1.0)
                        format_info = self.client_socket.recv(4, socket.MSG_PEEK)
                        
                        if format_info == b'HEVC':
                            # It's format info, read it and continue
                            self.client_socket.recv(4)  # Actually read the format info
                            print("Received HEVC format info")
                        else:
                            # Not format info, continue to frame reception
                            print(f"No format info or unknown format: {format_info}")
                    except socket.timeout:
                        print("No format info received within timeout, continuing to frame reception")
                    except Exception as e:
                        print(f"Error checking for format info: {e}")
                    
                    # Reset timeout to normal value for frame reception
                    self.client_socket.settimeout(5.0)
                    
                    # Receive video frames
                    self.receive_frames()
                    
                except ConnectionResetError:
                    print("Connection reset by client")
                except socket.timeout:
                    print("Connection timed out")
                except Exception as e:
                    print(f"Error during frame reception: {e}")
                finally:
                    print("Closing connection")
                    self.client_socket.close()
                    self.client_socket = None
                    
        except KeyboardInterrupt:
            print("Receiver stopped by user")
        except Exception as e:
            print(f"Error starting receiver: {e}")
        finally:
            self.stop()
    
    def receive_frames(self):
        """Receive and process video frames"""
        frame_count = 0
        start_time = time.time()
        
        # Create output video file
        output_filename = os.path.join(self.save_dir, "output.h265")
        output_file = None
        
        try:
            output_file = open(output_filename, 'wb')
            print(f"Saving video to: {output_filename}")
            
            while self.running:
                try:
                    # Receive frame size (4 bytes, big-endian as per Java ByteBuffer default)
                    size_data = self.client_socket.recv(4)
                    if not size_data:
                        print("No more data from client")
                        break
                    
                    # Check if we received a full 4 bytes for the size
                    if len(size_data) != 4:
                        print(f"Incomplete size data received: {len(size_data)}/4 bytes")
                        break
                    
                    # Unpack frame size (big-endian int, since Java/Kotlin uses big-endian by default)
                    frame_size = struct.unpack('>I', size_data)[0]
                    
                    print(f"[DEBUG] Received frame size: {frame_size} bytes")
                    
                    # Validate frame size (reasonable range)
                    if frame_size < 0 or frame_size > 10 * 1024 * 1024:  # Max 10MB per frame
                        print(f"Invalid frame size: {frame_size} bytes")
                        break
                    
                    # Receive frame data
                    frame_data = b''
                    remaining = frame_size
                    
                    while remaining > 0:
                        packet = self.client_socket.recv(min(remaining, 4096))
                        if not packet:
                            print(f"Connection closed while receiving frame data. Received {len(frame_data)}/{frame_size} bytes")
                            break
                        frame_data += packet
                        remaining -= len(packet)
                    
                    if remaining > 0:
                        print(f"Incomplete frame received: {len(frame_data)}/{frame_size} bytes")
                        break
                    
                    print(f"[DEBUG] Received frame data: {len(frame_data)} bytes")
                    
                    # Write frame data to output file
                    output_file.write(frame_data)
                    
                    frame_count += 1
                    
                    # Print status every 10 frames
                    if frame_count % 10 == 0:
                        elapsed_time = time.time() - start_time
                        fps = frame_count / elapsed_time if elapsed_time > 0 else 0
                        print(f"Received {frame_count} frames, FPS: {fps:.2f}")
                        
                except socket.timeout:
                    print("Socket timeout during frame reception, continuing...")
                    continue  # Continue instead of breaking
                except Exception as e:
                    print(f"Error receiving frame: {e}")
                    break
                    
        except Exception as e:
            print(f"Error opening output file: {e}")
        finally:
            if output_file:
                output_file.close()
                print(f"Video file saved to: {output_filename}")
        
        total_time = time.time() - start_time
        if total_time > 0:
            avg_fps = frame_count / total_time
        else:
            avg_fps = 0
        
        print(f"Frame reception completed. Total: {frame_count} frames, Average FPS: {avg_fps:.2f}")
    
    def stop(self):
        """Stop the video receiver"""
        self.running = False
        
        try:
            if self.client_socket:
                self.client_socket.close()
        except:
            pass
        
        try:
            if self.socket:
                self.socket.close()
        except:
            pass
        
        print("Video Receiver stopped")


if __name__ == "__main__":
    receiver = VideoReceiver()
    receiver.start()
