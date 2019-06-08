import socket, threading
import tcpServerThread

class TCPServer(threading.Thread):
    def __init__(self, commandQueue, HOST, PORT):
        threading.Thread.__init__(self)
        
        self.commandQueue = commandQueue
        self.HOST = HOST
        self.PORT = PORT
        
        self.serverSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.serverSocket.bind((self.HOST, self.PORT))
        self.serverSocket.listen(1)
        
        self.connections = []
        self.tcpServerThreads = []
    
    def run(self):
        try:
            while True:
                ipaddr = socket.gethostbyname(socket.getfqdn())
                print('The IP address of this server is [', ipaddr, ']')
                print ('tcp server :: server wait...')
                connection, clientAddress = self.serverSocket.accept()
                self.connections.append(connection)
                print ("tcp server :: connect :", clientAddress)
    
                subThread = tcpServerThread.TCPServerThread(self.commandQueue, self.tcpServerThreads, self.connections, connection, clientAddress)
                subThread.start()
                self.tcpServerThreads.append(subThread)
        except:
            print ("tcp server :: serverThread error")
 
    def sendAll(self, message):
        try:
            self.tcpServerThreads[0].send(message)
        except:
            pass
    
    def learnAll(self, message):
        try:
            self.tcpServerThreads[0].learn(message)
        except:
            pass
    
    def predictAll(self, message):
        try:
            self.tcpServerThreads[0].predict(message)
        except:
            pass