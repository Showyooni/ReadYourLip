# executer 클래스 정의
class Executer:
    # 기본 정의 def
    # andRaspTCP 모듈을 tcpServer와 연결짓는다 
    def __init__(self, tcpServer):
        self.andRaspTCP = tcpServer
        
    # 중요!!
    # 다음으로 각 command에서 받은 내용을 읽은 후, tcpServer 상에서 어떤 함수를 실행할 것인지를 정의한다.
    # 기억하기: tcpServerThread는 아직 연결짓지 않음!
    def startCommand(self, command):
        # 서버와 연결 여부를 android 상에서 확인하고자 할 경우 "checked\n"를 android 사용자에게 보낸다.
        if "connection" in command:
            self.andRaspTCP.sendAll("connected\n")
            
        # 학습을 하는 경우
        elif "learn" in command:
            self.andRaspTCP.learnAll(command)
            
        # 유추를 하는 경우
        elif "predict" in command:
            self.andRaspTCP.predictAll(command)