# executer Ŭ���� ����
class Executer:
    # �⺻ ���� def
    # andRaspTCP ����� tcpServer�� �������´� 
    def __init__(self, tcpServer):
        self.andRaspTCP = tcpServer
        
    # �߿�!!
    # �������� �� command���� ���� ������ ���� ��, tcpServer �󿡼� � �Լ��� ������ �������� �����Ѵ�.
    # ����ϱ�: tcpServerThread�� ���� �������� ����!
    def startCommand(self, command):
        # ������ ���� ���θ� android �󿡼� Ȯ���ϰ��� �� ��� "checked\n"�� android ����ڿ��� ������.
        if "connection" in command:
            self.andRaspTCP.sendAll("connected\n")
            
        # �н��� �ϴ� ���
        elif "learn" in command:
            self.andRaspTCP.learnAll(command)
            
        # ���߸� �ϴ� ���
        elif "predict" in command:
            self.andRaspTCP.predictAll(command)