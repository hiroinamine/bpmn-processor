bpmn-processor
======

API para processamento de BPM utilizando [Camunda](https://docs.camunda.org).

## Tecnologias
* [Scala](https://www.scala-lang.org/)
* [PlayFramework](playframework.com)
* [Camunda](https://docs.camunda.org)
* [json4s](https://github.com/json4s/json4s)
* [H2Database](https://www.h2database.com)

## Documentação da API

#### POST /bpmn/deploy

Deploy de um bpmn.

**Request:**

Enviar um arquivo `.bpmn` através através do formato `multipart/form-data`:

```html
<form action="/races" method="POST" enctype="multipart/form-data">
  <input type="file" name="helloworld.bpmn" />
  <button>Submit</button>
</form>
```

**Response:**
```json
{
  "deployId": "dd563f39-a2b8-41c0-8b68-e1160e510a55"
}
```

#### POST /bpmn/:id/fire

Executa um bpmn a partir do seu ID.

**Request:**

QueryString:
* `id` - campo **Id** em **General** no camunda-modeler

Body:
```json
{
  "name": "Mark",
  "key": "value..."
}
```


**Response:**

```json
{
  "sessionId": "15ef9696-66f1-421b-a864-14863031305d"
}
```
