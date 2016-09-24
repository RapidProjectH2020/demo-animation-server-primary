/*******************************************************************************
 * Copyright (C) 2015, 2016 RAPID EU Project
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA
 *******************************************************************************/

package eu.project.rapid.demo_animation_main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import eu.project.rapid.common.RapidMessages.AnimationMsg;

/**
 * A simple Java program, which listens for commands from several clients and updates the scenario
 * based on the commands. Used to show the interaction between the different entities in real-time.
 * To be used for the Rapid demos.
 * 
 * @author sokol
 *
 */
public class PrimaryAnimationServer {
	private static final String TAG = "PrimaryAnimationServer";
	private ServerSocket serverSocket;
	private static final int port = 6666;

	private static BlockingQueue<String> commandQueue;
	
	private static boolean isSecondAnimationServerConnected = false;

	public PrimaryAnimationServer() {

		commandQueue = new ArrayBlockingQueue<String>(1000);

		// Now can start listening for commands
		//    new Thread(new CommandHandler()).start();

		try {
			serverSocket = new ServerSocket(port);
			log(TAG, "Waiting for connections on port " + port);
			while (true) {
				Socket clientSocket = serverSocket.accept();
				log(TAG, "New client connected");
				new Thread(new ClientHandler(clientSocket)).start();
			}

		} catch (IOException e) {
			log(TAG, "Error while creating server socket: " + e);
		}
	}

	private class ClientHandler implements Runnable {

		private static final String TAG = "AnimationClientHandler";
		private Socket clientSocket;
		private PrintWriter out;
		private BufferedReader in;

		public ClientHandler(Socket clientSocket) {
			this.clientSocket = clientSocket;
		}

		@Override
		public void run() {

			try {
				out = new PrintWriter(clientSocket.getOutputStream(), true);
				in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

				String command = in.readLine();
				log(TAG, "Received command: " + command);

				if (command.equals("GET_COMMANDS")) {
					// Forward the commands to the animation server running on a machine
					// that can visualize figures.
					if (!isSecondAnimationServerConnected) {
						log(TAG, "The second animation server is connected");
						isSecondAnimationServerConnected = true;
						new Thread(new SecondAnimationPusher(clientSocket, in, out)).start();
//						new Thread(new FakeMessageGenerator()).start();
					} else {
						log(TAG, "Error, only one second animation server is allowed to connect");
					}
					
				} else {
					while (command != null) {

						if (command.equals("PING")) {
							continue;
						} else {
							try {
								commandQueue.put(command);
								out.println("0");
								log(TAG, "Inserted command: " + command);
							} catch (InterruptedException e) {
								System.err.println("InterruptedException inserting command: " + e);
							}
						}
						command = in.readLine();
					}
					if (out != null)
						out.close();
					if (in != null) {
						try {
							in.close();
						} catch (IOException e) {
						}
					}
					if (clientSocket != null) {
						try {
							clientSocket.close();
						} catch (IOException e) {
						}
					}
				}

			} catch (IOException e) {
				log(TAG, "Error while talking to client: " + e);
			} 
		}
	}

	private class SecondAnimationPusher implements Runnable {

		private static final String TAG = "CommandHandler";
		private Socket clientSocket;
		private BufferedReader in;
		private PrintWriter out;

		public SecondAnimationPusher(Socket clientSocket, BufferedReader in, PrintWriter out) {
			this.clientSocket = clientSocket;
			this.in = in;
			this.out = out;
		}

		@Override
		public void run() {

			new Thread(new SecondAnimationReader(clientSocket, in, out)).start();
			
			while (true) {
				log(TAG, "Waiting for commands to push to the second animation server");
				if (isSecondAnimationServerConnected) {
					String command;
					try {
						// log(TAG, "Waiting for command...");
						command = commandQueue.take();

						log(TAG, "Sending command to secondary animation server: " + command);
						out.println(command);
						out.flush();

					} catch (InterruptedException e) {
						log(TAG, "Error while reading from the queue: " + e);
					} catch(Exception e) {
						log(TAG, "Error while pushing to second animation server: " + e);
					}
				} else {
					log(TAG, "There is no second animation server connected to push messages");
					break;
				}
			} 
		}
	}

	private class SecondAnimationReader implements Runnable {

		private static final String TAG = "CommandHandler";
		private Socket clientSocket;
		private BufferedReader in;
		private PrintWriter out;

		public SecondAnimationReader(Socket clientSocket, BufferedReader in, PrintWriter out) {
			this.clientSocket = clientSocket;
			this.in = in;
			this.out = out;
		}

		@Override
		public void run() {

			String c = null;
			try {
				log(TAG, "Started waiting for comands from the second animation server");
				while ((c = in.readLine()) != null && !c.equals("QUIT")) {
					log(TAG, "Second animation server sent command: " + c);
				}
			} catch (IOException e) {
				log(TAG, "Connection with client interrupted: " + e);
			} finally {
				log(TAG, "Secondary animation server is gone");
				isSecondAnimationServerConnected = false;
				
				if (out != null)
					out.close();
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) {
					}
				}
				if (clientSocket != null) {
					try {
						clientSocket.close();
					} catch (IOException e) {
					}
				}
			}
		}
	}
	
	private class FakeMessageGenerator implements Runnable {

		@Override
		public void run() {
			
			AnimationMsg[] msgs = AnimationMsg.values();
			
			for (int i = 0; i < 10; i++) {
				try {
					Thread.sleep(20 * 1000);
					commandQueue.put(msgs[i].toString());
				} catch (InterruptedException e) {
					log(TAG, "Could not insert message in the queue: " + e);
				}
			}
		}
	}
	
	private void log(String tag, String msg) {
		System.out.println("[" + tag + "]: " + msg);
	}

	public static void main(String[] args) {
		new PrimaryAnimationServer();
	}
}
