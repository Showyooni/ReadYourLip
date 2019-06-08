import socket, threading
from sklearn.cluster import KMeans
from sklearn.externals import joblib
import math
import numpy as np
import pandas as pd
import seaborn as sb
import matplotlib.pyplot as plt

class TCPServerThread(threading.Thread):
    def __init__(self, commandQueue, tcpServerThreads, connections, connection, clientAddress):
        threading.Thread.__init__(self)
 
        self.commandQueue = commandQueue
        self.tcpServerThreads = tcpServerThreads
        self.connections = connections
        self.connection = connection
        self.clientAddress = clientAddress
 
    def run(self):
        try:
            while True:
                data = self.connection.recv(1024).decode()
 
                # when break connection
                if not data:
                    print ('tcp server :: exit :',self.connection)
                    break
 
 
                print ('tcp server :: send data to client :', data)
                self.commandQueue.put(data)
        except:
            self.connections.remove(self.connection)
            self.tcpServerThreads.remove(self)
            exit(0)
        self.connections.remove(self.connection)
        self.tcpServerThreads.remove(self)
 
    def send(self, message):
        print ('TCP server :: ',message)
        try:
            for i in range(len(self.connections)):
                self.connections[i].sendall(message.encode())
        except:
             pass
         
    def learn(self, message):
        try:
            for i in range(len(self.connections)):
                print('start learning')
        
                #�켱 �ʿ��� ������ �θ���
                learningFileName = 'learning_1_saved.sav'
                df = joblib.load(learningFileName)
                
                # �ش� ���Ͽ����� DataFrame (df)�� �ҷ��� ����غ���
                sb.lmplot('x','y',data = df, fit_reg=False, scatter_kws={"s":150}, hue="cluster")
                plt.title('Before')
                
                #���� df2�� ������ index ��ȣ�� ���Ѵ�
                lastIndex = len(df.index)-1
                #�� �� message���� ���ڸ� �����ϰ� ���ο� ���� �ֵ� �ʱ� cluster ��ȣ�� 0���� �����Ѵ�.
                Xtext = message[message.index('_')+1:message.index(',')]
                Ytext = message[message.index(',')+1:message.index('.')]
                inputX = int(Xtext)
                inputY = int(Ytext)
                df.loc[lastIndex+1] = [inputX,inputY,0]
                
                print('Received input: ', inputX, ', ', inputY)
                # ���н��� �����Ѵ�
                newpoints = df.values
                kmeans = KMeans(n_clusters=5).fit(newpoints)

                # ���ο� 'cluster' ���� �ٿ��ְ� ���� ����� ����Ѵ�.
                df['cluster'] = kmeans.labels_
                sb.lmplot('x','y',data = df, fit_reg=False, scatter_kws={"s":150}, hue="cluster")
                plt.title('After')
                
                # �� Ŭ�������� �߽ɰ��� �о����
                clusterData = kmeans.cluster_centers_
                
                # �� clusterData�� X�� �������� �����ϱ�
                sortedCluster = clusterData[clusterData[:,0].argsort()]
                
                # ���⼭ 3��° ��(Ŭ������ ��ȣ)�� �߶󳽴�
                finalCutCluster = np.delete(sortedCluster, np.s_[2], axis=1)
                
                #���� ����� ���Ͽ� �����ϰ� (�������� + Ŭ������)
                joblib.dump(df, learningFileName)
                np.save('clusterCenter',finalCutCluster)
                print("Learning is complete!")
                
                #���������� �����ߴٴ� �޼����� ������
                newMessage = "complete\n"
                print(type(newMessage))
                self.connections[i].sendall(newMessage.encode())
                print("(", newMessage, ") has been sent to client")
        except:
            pass
    
    
    def predict(self, message):
        print ('tcp server :: ',message)
        try:
            for i in range(len(self.connections)):
                # cluster �߾Ӱ� ��̸� ������ ������ �ҷ��´�
                loadClusterCenter = np.load('clusterCenter.npy')
                
                # input�� �����Ѵ�. (message���� ���ڸ� �����Ѵ�)
                Xtext = message[message.index('_')+1:message.index(',')]
                Ytext = message[message.index(',')+1:message.index('.')]
                inputX = int(Xtext)
                inputY = int(Ytext)
                print('Received input: ', inputX, ', ', inputY)
                
                # clusterCenter����� ������� ���̵��� resultList�μ� ���ȭ�Ѵ�.
                resultList = []
                for i in range(0,len(loadClusterCenter)):
                    temp = pow(inputX-loadClusterCenter[i][0], 2) + pow(inputY-loadClusterCenter[i][1], 2)
                    resultList.append(math.sqrt(temp))

                # resultList �߿��� ���� ª�� ������ index���� ã�ƺ���
                indexOfShortest = resultList.index(min(resultList))

                # ����غ���
                print('Index of the shortest: ', indexOfShortest)
                
                # �� indexOfShortest�� ������
                newMessage = str(indexOfShortest) + "\n"
                print(type(newMessage))
                self.connections[i].sendall(newMessage.encode())
                print("(", newMessage, ") has been sent to client")
        except:
            pass
            