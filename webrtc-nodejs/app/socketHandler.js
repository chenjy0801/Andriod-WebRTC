//var list=[];
//var connList=[];
module.exports = function(io, streams) {
  var list=[];
  var connList=[];

  /**
   * connection
   */
  io.on('connection', function(client) {
    console.log('-- ' + client.id + ' joined --');
    client.emit('id', client.id);
    // send streaming user list to all client connect to the socket
    //console.log(list);
    var jsonList = {"ret":list};
    client.emit('list',jsonList);
    console.log('jsonList : ');
    console.log(list)

    /**
    * message
    */
    client.on('message', function (details) {
      var otherClient = io.sockets.connected[details.to];

      if (!otherClient) {
        return;
      }
        delete details.to;
        details.from = client.id;
        otherClient.emit('message', details);
        console.log('details:'+ ' from: '+ details.from +' type:' + details.type);
    });
      
    /**
    * readyToStream
    */
    client.on('readyToStream', function(options) {
      // push new streaming client's id to the list
      // traverse list to find if id is already existed
      var map = {"ID":client.id}
      let cnt=0;
      for(let i=0;i<list.length;i++){
        if(list[i]["ID"]!=client.id){
          cnt++;
        }
      }
      // if id not exist, push to the list
      if(cnt==list.length){
        list.push(map);
      }
      // send id to all users
      io.emit('id',client.id);
      console.log('-- ' + client.id + ' is ready to stream --');
      
      streams.addStream(client.id, options.name); 
    });
    
    //client.on('update', function(options) {
    //  streams.update(client.id, options.name);
    //});

    /**
    * onCall: client connect to others
    */
    client.on('onCall', function(options) {
      io.emit('id',client.id);
      io.emit('id',options.callId);
      console.log('-- ' + client.id + ' stream again--');
      console.log(options.callId);
      
      // push both id to connList
      var map = {"id1":client.id, "id2":options.callId};
      connList.push(map);
      console.log(connList);
    })


    /**
    * leave
    */
    function leave() {
      console.log('-- ' + client.id + ' left --');
      streams.removeStream(client.id);
      // delete leaving client's id from the list
      for(let i=0;i<list.length;i++){
        if(list[i]["ID"]==client.id){
          list.splice(i,1);
          console.log('after leave: ');
          console.log(list)
        }
      }

      // if leaving client already connect to others, delete both id from connList
      let cnt=0;
      for(let i=0;i<connList.length;i++){
        if(connList[i]["id1"]==client.id || connList[i]["id2"]==client.id){
            connList.splice(i,1);  
            cnt++;
        }
      }
      if(cnt==connList.length){
        io.emit('id',client.id);
      }
    }

    client.on('disconnect', leave);
    client.on('leave', leave);

  });
};