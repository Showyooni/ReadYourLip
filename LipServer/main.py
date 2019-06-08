import tcpServer
import executer
from multiprocessing import Queue 

commandQueue = Queue()
 
andRaspTCP = tcpServer.TCPServer(commandQueue, "", 7070)
andRaspTCP.start()
 
 
commandExecuter = executer.Executer(andRaspTCP)
 
while True:
    try:
        command = commandQueue.get()
        commandExecuter.startCommand(command)
    except:
        pass
