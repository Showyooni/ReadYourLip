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
        
                #우선 필요한 파일을 부른다
                learningFileName = 'learning_1_saved.sav'
                df = joblib.load(learningFileName)
                
                # 해당 파일에서의 DataFrame (df)를 불러서 출력해본다
                sb.lmplot('x','y',data = df, fit_reg=False, scatter_kws={"s":150}, hue="cluster")
                plt.title('Before')
                
                #이전 df2의 마지막 index 번호를 구한다
                lastIndex = len(df.index)-1
                #그 후 message에서 숫자를 추출하고 새로운 값을 넣되 초기 cluster 번호를 0으로 지정한다.
                Xtext = message[message.index('_')+1:message.index(',')]
                Ytext = message[message.index(',')+1:message.index('.')]
                inputX = int(Xtext)
                inputY = int(Ytext)
                df.loc[lastIndex+1] = [inputX,inputY,0]
                
                print('Received input: ', inputX, ', ', inputY)
                # 재학습을 실행한다
                newpoints = df.values
                kmeans = KMeans(n_clusters=5).fit(newpoints)

                # 새로운 'cluster' 라벨을 붙여주고 러닝 결과를 출력한다.
                df['cluster'] = kmeans.labels_
                sb.lmplot('x','y',data = df, fit_reg=False, scatter_kws={"s":150}, hue="cluster")
                plt.title('After')
                
                # 각 클러스터의 중심값을 읽어오기
                clusterData = kmeans.cluster_centers_
                
                # 이 clusterData를 X축 기준으로 정렬하기
                sortedCluster = clusterData[clusterData[:,0].argsort()]
                
                # 여기서 3번째 열(클러스터 번호)를 잘라낸다
                finalCutCluster = np.delete(sortedCluster, np.s_[2], axis=1)
                
                #최종 결과를 파일에 저장하고 (러닝파일 + 클러스터)
                joblib.dump(df, learningFileName)
                np.save('clusterCenter',finalCutCluster)
                print("Learning is complete!")
                
                #마지막으로 성공했다는 메세지를 보내기
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
                # cluster 중앙값 어레이를 저장한 파일을 불러온다
                loadClusterCenter = np.load('clusterCenter.npy')
                
                # input을 지정한다. (message에서 숫자를 추출한다)
                Xtext = message[message.index('_')+1:message.index(',')]
                Ytext = message[message.index(',')+1:message.index('.')]
                inputX = int(Xtext)
                inputY = int(Ytext)
                print('Received input: ', inputX, ', ', inputY)
                
                # clusterCenter들과의 상대적인 길이들을 resultList로서 목록화한다.
                resultList = []
                for i in range(0,len(loadClusterCenter)):
                    temp = pow(inputX-loadClusterCenter[i][0], 2) + pow(inputY-loadClusterCenter[i][1], 2)
                    resultList.append(math.sqrt(temp))

                # resultList 중에서 가장 짧은 길이의 index값을 찾아본다
                indexOfShortest = resultList.index(min(resultList))

                # 출력해보기
                print('Index of the shortest: ', indexOfShortest)
                
                # 이 indexOfShortest를 보낸다
                newMessage = str(indexOfShortest) + "\n"
                print(type(newMessage))
                self.connections[i].sendall(newMessage.encode())
                print("(", newMessage, ") has been sent to client")
        except:
            pass
            