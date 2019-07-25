package org.hiroinamine.domains

/** Camunda custom failures **/
case class ResponseTimeoutFailure(msg: String) extends RuntimeException(msg)
case class MessageNotFoundFailure(msg: String) extends RuntimeException(msg)
