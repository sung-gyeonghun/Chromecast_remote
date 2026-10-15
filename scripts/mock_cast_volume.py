"""Loopback-only Cast volume fixture; use a throwaway PEM identity, never production keys."""
import json, ssl, socketserver, struct, sys, threading

def varint(n):
    out=bytearray()
    while n>127: out.append((n&127)|128);n>>=7
    out.append(n);return bytes(out)
def text(field,value):
    raw=value.encode();return varint((field<<3)|2)+varint(len(raw))+raw
def parse(raw):
    pos=0;fields={}
    def number():
        nonlocal pos
        value=shift=0
        while pos<len(raw) and shift<64:
            b=raw[pos];pos+=1;value|=(b&127)<<shift
            if b<128:return value
            shift+=7
        raise ValueError('Malformed protobuf')
    while pos<len(raw):
        tag=number();field=tag>>3;wire=tag&7
        if wire==0: fields[field]=number()
        elif wire==2:
            size=number();fields[field]=raw[pos:pos+size];pos+=size
        else:raise ValueError('Unsupported wire')
    return fields
class Handler(socketserver.BaseRequestHandler):
    def handle(self):
        with self.server.tls.wrap_socket(self.request,server_side=True) as s:
            s.settimeout(10)
            def read(n):
                out=b''
                while len(out)<n:
                    part=s.recv(n-len(out))
                    if not part:raise EOFError()
                    out+=part
                return out
            def send(ns,payload):
                raw=b'\x08\x00'+text(2,'receiver-0')+text(3,'remocon-volume')+text(4,ns)+b'\x28\x00'+text(6,json.dumps(payload))
                s.sendall(struct.pack('>I',len(raw))+raw)
            try:
                while True:
                    size=struct.unpack('>I',read(4))[0]
                    if not 0<size<1048576:raise ValueError('Invalid frame')
                    fields=parse(read(size));ns=fields[4].decode();data=json.loads(fields[6])
                    if ns!='urn:x-cast:com.google.cast.receiver':continue
                    kind=data['type']
                    if kind=='SET_VOLUME':
                        with self.server.lock:self.server.volume.update(data['volume'])
                        print('CAST_SET_VOLUME='+json.dumps(self.server.volume),flush=True)
                    elif kind!='GET_STATUS':raise ValueError(kind)
                    send(ns,{'type':'RECEIVER_STATUS','requestId':data['requestId'],'status':{'volume':self.server.volume}})
            except EOFError:pass
class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address=True
    daemon_threads=True
    volume={'level':.5,'muted':False,'controlType':'master'}
    lock=threading.Lock()
with Server(('127.0.0.1',8009),Handler) as server:
    server.tls=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);server.tls.load_cert_chain(sys.argv[1])
    print('CAST_MOCK_READY=127.0.0.1:8009',flush=True)
    server.serve_forever()
