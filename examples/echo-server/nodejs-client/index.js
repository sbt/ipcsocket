const ipc = require('socket-ipc')

const client = new ipc.MessageClient('/tmp/socket-loc.sock')

client.on('connection', function (connection) {
  console.log('connected. sending greetings...')
  client.send('greetings\n')
})

client.on('message', function (message) {
  console.log('got message:', message.data)
})

client.start()