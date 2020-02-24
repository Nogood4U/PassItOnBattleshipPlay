package services

import play.api.libs.json.{JsError, JsSuccess, JsValue, Json, Reads}


object BoardDataParser {

  implicit val positionReader: Reads[Position] = Json.reads[Position]
  implicit val pieceReader: Reads[Piece] = Json.reads[Piece]

  def parse(json: JsValue): Option[List[Piece]] = json.validate[List[Piece]] match {
    case JsSuccess(value, path) => Some(value)
    case JsError(errors) => println(errors); None
  }

}

case class Piece(size: Int, positions: List[Position])

case class Position(x: Int, y: Int)
