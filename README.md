
# Akka typed-graph-db

## How to run tests

To run all tests:

```
./sbt.sh test
```


## Running Service Cluster

The application is cluster-ready. When Application started it will start embedded db servers.
Please check application.conf for configuration settings

1. Start  graphdb 

```
./sbt.sh runMain
```

## Interact With the Application via REST

Add node in the Graph:

```
curl -X POST -H "Content-Type: application/json" -d '{"nodeType":"person", "properties":[{"attributeType":"string", "name":"lastname", "value":"Alba"}, {"attributeType":"string", "name":"firstname", "value":"Jessiaca"}, {"attributeType":"number", "name":"age", "value":"20"}]}' http://127.0.0.1:8053/nodes
```

Example of result:

`069a5f83-5bc0-49c5-a2bd-9ac6d526ee34`


Get the node of the Graph:

```
curl http://127.0.0.1:8081/nodes/{nodeId}
```

Example of result:

```json
{"nodeId":"069a5f83-5bc0-49c5-a2bd-9ac6d526ee34","nodeType":"person","properties":{"age":20.0,"firstname":"Jessiaca","lastname":"Alba"},"relations":{}}
```

Add field to the Graph:

```
curl -X PUT -H "Content-Type: application/json" -d '{"attributeType":"string", "name":"stock", "value":"DAL"}' http://127.0.0.1:8081/nodes/fields/{nodeId}
```

Example of result:

`OK`

Remove the field of the node:

```
curl -X DELETE -H "Content-Type: application/json" -d '{"name":"stock"}' http://127.0.0.1:8081/nodes/fields/{nodeId}
```

Example of result:

`OK`
