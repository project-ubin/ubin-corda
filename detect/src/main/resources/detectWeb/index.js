// var port = ""
//
// function getPort() {
//   let portMap = {
//     "NodeA": 10007,
//     "NodeB": 10010,
//     "NodeC": 10013,
//     "NodeD": 10016,
//     "NodeE": 10019,
//     "NodeF": 10022
//   }
//   let me = fetch('http://localhost:10007/api/base/me', { method: 'get' })
//     .then((resp) => resp.json().then((result) => {
//       console.log(result.me)
//       port = portMap[result.me]
//     }))
// }
//
// let input = fetch('http://localhost:10007/api/detect/start-scan?currency=SGD', { method: 'get' })
// .then((resp) => resp.json().then((json) => {
//   let graph = json
//
//   // Get set of nodes.
//   let nodesSet = new Set()
//   graph.obligations.forEach( edge => {
//     nodesSet.add(edge.lender).add(edge.borrower)
//   })
//
//   // Create nodes.
//   let nodes = Array.from(nodesSet).map(node => ([ node.substring(4), { toString: () => node.substring(4) } ]))
//   console.log(nodes)
//
//   // Create edges.
//   let edges = []
//   graph.obligations.forEach( edge => {
//     edges.push([edge.borrower.substring(4), edge.lender.substring(4), { amount: edge.amount/100 }])
//   })
//
//   // Create new graph instance.
//   var G = new jsnx.DiGraph()
//
//   // Add edges and nodes.
//   G.addNodesFrom(nodes)
//   G.addEdgesFrom(edges)
//
//   // Draw the graph.
//   jsnx.draw(G, {
//       element: '#canvas',
//       layoutAttr: {
//         charge: -200,
//         linkDistance: 150
//       },
//       withLabels: true,
//       weighted: true,
//       withEdgeLabels: true,
//       edgeLabels: "amount",
//       edgeStyle: {
//         'stroke-width': 5,
//         fill: '#999'
//       },
//       nodeStyle: {
//         stroke: 'none',
//         style: 'node',
//         fill: 'red',
//       },
//       labelStyle: {
//         fill: 'white'
//       },
//       stickyDrag: true
//   })
//
//   // Display limits.
//   graph.limits.forEach( limit => {
//     console.log(limit)
//     let para = document.createElement("p");
//     let node = document.createTextNode(limit.party + ": " + limit.limit/100);
//     para.appendChild(node);
//     let element = document.getElementById("limits");
//     element.appendChild(para);
//   })
// }))