# -*- coding: utf-8 -*-
import socket
from threading import Thread 
import random
import sys
import os

ENCODING = "ascii"
NEWLINE = "\r\n"
USERNAME = "bilkentstu"
PASS = "cs421s2020"

SERVER_SHUTDOWN_MESSAGE = "Server shutdown. Please fix your code according to the response message and retry."


HEADER_SIZE = 2
MAX_DATA_SIZE = 2**(HEADER_SIZE*8) - 1

# Socket stuff
IP = sys.argv[1]
CONTROL_PORT = int(sys.argv[2])


class ServerShutdownException(Exception):
    pass
 
class VersionConflictException(Exception):
    pass

class VersionMatchException(Exception):
    pass

class InvalidVersionException(Exception):
    pass

class LineMismatchException(Exception):
    pass



class ClientThread(Thread): 

    def __init__(self, conn): 
        Thread.__init__(self)
        self.conn = conn
 
    def run(self): 
        global version
        global file
        try:
            print("Client connected.")

            f = self.conn.makefile(buffering=1, encoding=ENCODING, newline=NEWLINE)

            # Authenticate and get client data port
            check = auth_check(f, self.conn)
            if check == False:
                shutdown()
                
            while True : 
                cmd, args = receive_command(f)
                
                if cmd == "APND":               
                    dissected_args = args.split(" ", 1)
                    try:
                        if len(dissected_args) == 2 and int(dissected_args[0]) == version:
                            append(dissected_args[1], file)
                            version += 1
                            send_response(self.conn, success=True, info = str(version))
                        else:
                            raise VersionConflictException

                    except VersionConflictException:
                        send_response(self.conn, success=False, info=str(version) + " is the current version, please get an update.")

                elif cmd == "WRTE":
                    dissected_args = args.split(" ", 2)
                    try:
                        if len(dissected_args) == 3 and int(dissected_args[0]) == version:
                            write(int(dissected_args[1]), dissected_args[2], file)
                            version += 1
                            send_response(self.conn, success=True, info = str(version))
                        else:
                            raise VersionConflictException

                    except VersionConflictException:
                        send_response(self.conn, success=False, info=str(version) + " is the current version, please get an update.")

                    except LineMismatchException:
                        send_response(self.conn, success=False, info= "No such line exists.")                        
                            
                elif cmd == "UPDT":
                    try:
                        if int(args) != version:
                            content = update(file)
                            send_updt_response(self.conn, success=True, info = str(version), content = content)
                        elif int(args) == version:
                            raise VersionMatchException
                        else:
                            raise InvalidVersionException

                    except VersionMatchException:
                        send_response(self.conn, success=False, info = str(version) + " is already the last version.")

                    except InvalidVersionException:
                        send_response(self.conn, success=False, info = str(version) + " is invalid for Update.")
                            
                elif cmd == "EXIT":
                    send_response(self.conn, success=True)
                    break
                
                elif cmd in ["USER", "PASS"]:
                    send_response(self.conn, success=False, info = cmd + " command is already sent and processed.")
                    shutdown()
                    
                else:
                    send_response(self.conn, success=False, info = "Unknown command.")
                    shutdown()
            
        except ServerShutdownException:
            pass
        
        except ConnectionResetError as e:
            print(e)
            
        finally:
            self.conn.close()
        

#Functions
def send_response(s, success, info=""):
    response = "OK" + " " + info if success else "INVALID " + info
    response = response + "\r\n"
    s.sendall(response.encode())

def send_updt_response(s, success, info="", content = ""):
    response = "OK" + " " + info + " " + content if success else "INVALID " + info
    response = response + "\r\n"
    s.sendall(response.encode())

def receive_command(f):
    line = f.readline()[:-len(NEWLINE)]
    idx = line.find(" ")
    
    if idx == -1:
        idx = len(line)
    
    cmd = line[:idx]
    args = line[idx+1:]
    print("Command received:", cmd, args)
    return cmd, args

def shutdown():
    print(SERVER_SHUTDOWN_MESSAGE)
    raise ServerShutdownException

def auth_check(f, conn):
    
    # Username check
    check = False
    cmd, args = receive_command(f)
    
    if cmd == "USER":
        if args == USERNAME:
            send_response(conn, success=True)
            check = True
        else:
            send_response(conn, success=False, info="Wrong username.")
    else:
        send_response(conn, success=False, info="Wrong command. Expecting USER.")
        
    if not check:
        return check
        
    # Password check
    check = False
    cmd, args = receive_command(f)
    if cmd == "PASS":
        if args == PASS:
            send_response(conn, success=True)
            check = True
        else:
            send_response(conn, success=False, info="Wrong password.")
    else:
        send_response(conn, success=False, info="Wrong command. Expecting PASS.")
    
    if not check:
        return check
        
def append(dargs, filepointer):
    filepointer.seek(0,2)
    filepointer.write(dargs + "\n")
    filepointer.flush()

def write(linenum, arg, filepointer):
    filepointer.seek(0,0)
    linelist = filepointer.readlines()
    if linenum > len(linelist):
        raise LineMismatchException
    filepointer.seek(0,0)
    linelist[linenum] = arg + "\n"
    filepointer.writelines(linelist)

def update(filepointer):
    filepointer.seek(0,0)
    linelist = filepointer.readlines()
    content = ""
    for i in linelist:
        content = content + i
    return content

# =============================================================================
# MAIN
# =============================================================================
if __name__ == "__main__":

    version = 0

    file = open("CS421_2020SPRING_PA1.txt","r+") 


    tcpServer = socket.socket(socket.AF_INET, socket.SOCK_STREAM) 
    tcpServer.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1) 
    tcpServer.bind((IP, CONTROL_PORT)) 
    threads = [] 
     
    while True: 
        tcpServer.listen(5) 
        print ("TextEditor Server : Waiting for connections..." )
        (conn, (ip,port)) = tcpServer.accept() 
        newthread = ClientThread(conn) 
        newthread.start() 
        threads.append(newthread) 
     

    for t in threads: 
        t.join() 
    tcpServer.close()
    file.close()
    
