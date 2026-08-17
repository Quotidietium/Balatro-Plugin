// 最小 RCON 客户端（模块 + CLI）
const net = require('net');

function pkt(reqId, type, payload) {
  const body = Buffer.alloc(4 + 4 + payload.length + 2);
  body.writeInt32LE(reqId, 0);
  body.writeInt32LE(type, 4);
  body.write(payload, 8, 'utf8');
  const head = Buffer.alloc(4);
  head.writeInt32LE(body.length, 0);
  return Buffer.concat([head, body]);
}

function rconCommand(host, port, password, command, timeoutMs = 8000) {
  return new Promise((resolve, reject) => {
    const sock = net.connect(port, host);
    let buf = Buffer.alloc(0);
    let reqId = Math.floor(Math.random() * 100000) + 1;
    let authed = false, answered = false;
    const to = setTimeout(() => { sock.destroy(); reject(new Error('rcon timeout')); }, timeoutMs);
    sock.on('connect', () => sock.write(pkt(reqId, 3, password)));
    sock.on('data', (d) => {
      buf = Buffer.concat([buf, d]);
      while (buf.length >= 12) {
        const len = buf.readInt32LE(0);
        if (buf.length < 4 + len) break;
        const id = buf.readInt32LE(4);
        const type = buf.readInt32LE(8);
        const payload = buf.slice(12, 4 + len - 2).toString('utf8');
        buf = buf.slice(4 + len);
        if (!authed) {
          if (id === -1) { clearTimeout(to); sock.destroy(); return reject(new Error('rcon auth failed')); }
          authed = true;
          sock.write(pkt(reqId, 2, command));
        } else {
          answered = true;
          clearTimeout(to);
          sock.destroy();
          resolve(payload);
        }
      }
    });
    sock.on('error', (e) => { clearTimeout(to); reject(e); });
  });
}

module.exports = { rconCommand };
if (require.main === module) {
  const cmd = process.argv.slice(2).join(' ') || 'list';
  rconCommand('127.0.0.1', 25575, 'balatro220', cmd).then(out => {
    console.log('RCON>', cmd);
    console.log(out || '(empty)');
    process.exit(0);
  }).catch(e => { console.error('RCON ERR', e.message); process.exit(1); });
}
