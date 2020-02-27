package services

import controllers.BattleUser
import controllers.Roles.Role
import models.BattlePlayer
import play.api.libs.json.{JsError, JsString, JsSuccess, JsValue, Json, OWrites, Reads, Writes}


object BoardDataParser {

  implicit val positionReader: Reads[Position] = Json.reads[Position]
  implicit val pieceReader: Reads[Piece] = Json.reads[Piece]

  implicit val roleWrites: Writes[Role] = (o: Role) => JsString(o.name)
  implicit val userWrites: OWrites[BattleUser] = Json.writes[BattleUser]
  implicit val playerWrites: OWrites[BattlePlayer] = Json.writes[BattlePlayer]
  implicit val positionWrites: OWrites[Position] = Json.writes[Position]
  implicit val pieceWrites: OWrites[Piece] = Json.writes[Piece]
  implicit val gameBoxWrites: OWrites[GameBox] = Json.writes[GameBox]
  implicit val gamePieceWrites: OWrites[GamePiece] = Json.writes[GamePiece]
  implicit val gameBoardWrites: OWrites[GameBoard] = Json.writes[GameBoard]
  implicit val gameEntryWrites: OWrites[GameEntry] = Json.writes[GameEntry]
  implicit val gameStatusWrites: Writes[GameStatus.Status] = (o: GameStatus.Status) => JsString(o.status)
  implicit val gameStateWrites: OWrites[GameState] = Json.writes[GameState]

  def parseBoard(json: JsValue): Option[List[Piece]] = json.validate[List[Piece]] match {
    case JsSuccess(value, path) => Some(value)
    case JsError(errors) => println(errors); None
  }

  def parsePiece(json: JsValue): Option[Piece] = json.validate[Piece] match {
    case JsSuccess(value, path) => Some(value)
    case JsError(errors) => println(errors); None
  }

}

case class Piece(size: Int, positions: List[Position])

case class Position(x: Int, y: Int)
