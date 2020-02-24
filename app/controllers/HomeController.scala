package controllers

import java.util.UUID

import akka.actor.ActorSystem
import auth.config.AuthBattleUserMongoService
import cats.data.OptionT
import cats.implicits._
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import services.BattlePlayerService
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator
import com.mohiva.play.silhouette.impl.providers.{OAuth2Info, OAuth2Provider}
import controllers.Roles.{AdminRole, Role, UserRole}
import game.player.OnlinePlayer.Disconnected
import javax.inject._
import models.BattlePlayer
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future


/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents,
                               protected val dbConfigProvider: DatabaseConfigProvider,
                               silhouette: Silhouette[DefaultEnv],
                               @Named("provider-registry")
                               providerRegistry: Map[String, OAuth2Provider],
                               userService: AuthBattleUserMongoService,
                               eventBus: EventBus,
                               battlePlayerService: BattlePlayerService,
                               authenticatorService: AuthenticatorService[JWTAuthenticator])(implicit actorSystem: ActorSystem) extends AbstractController(cc) {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */

  implicit val roleWrites: Writes[Role] = (o: Role) => JsString(o.name)
  implicit val userWrites: OWrites[BattleUser] = Json.writes[BattleUser]
  implicit val playerWrites: OWrites[BattlePlayer] = Json.writes[BattlePlayer]

  def index: Action[AnyContent] = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    (for {
      player <- OptionT(battlePlayerService.getBattlePlayer(request.identity.loginInfo.providerKey))
      status <- OptionT.liftF(battlePlayerService.getPlayerStatus(player))
    } yield player -> status.status.map(_.toString).orNull).value.map {
      case Some(value) => Ok(
        Json.obj(
          "user" -> Json.toJson(request.identity.asInstanceOf[BattleUser]),
          "player" -> Json.toJson(value._1),
          "status" -> JsString(value._2)))
      case None => NotFound
    }.recover {
      case e => e.printStackTrace(); NotFound
    }
  }

  def updatePlayer(playerId: String): Action[AnyContent] = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    request.body.asJson.map(jsonData => {

      val tagUpdate: Option[Future[BattlePlayer]] = (jsonData \ "playerTag").toOption.map(playerTag => {
        (for {
          optPlayer <- battlePlayerService.getBattlePlayer(playerId)
          player: Option[BattlePlayer] <- if (optPlayer.isDefined) battlePlayerService.getBattlePlayer(optPlayer.get.userInfoId)
          else Future.successful(None)
          uPlayer <- if (player.isDefined) battlePlayerService.saveBattlePlayer(player.get.copy(playerTag = playerTag.as[String]))
          else Future.failed[BattlePlayer](null)
        } yield uPlayer)
      })

      val profileUpdate: Option[Future[BattlePlayer]] = (jsonData \ "publicProfile").toOption.map(isPublic => {
        (for {
          optPlayer <- battlePlayerService.getBattlePlayer(playerId)
          player: Option[BattlePlayer] <- if (optPlayer.isDefined) battlePlayerService.getBattlePlayer(optPlayer.get.userInfoId)
          else Future.successful(None)
          uPlayer <- if (player.isDefined) battlePlayerService.saveBattlePlayer(player.get.copy(public = isPublic.as[Boolean]))
          else Future.failed[BattlePlayer](null)
        } yield uPlayer)
      })

      val futureOpt: Future[BattlePlayer] = tagUpdate.orElse(profileUpdate).get
      (for {
        player <- futureOpt
      } yield Ok(Json.toJson(player))).recoverWith {
        case _ => Future.successful(BadRequest)
      }

    }).getOrElse(Future.successful(BadRequest))
  }

  def providerList: Action[AnyContent] = Action {
    Ok(Json.obj("providers" -> providerRegistry.keys.toList))
  }

  def adminOnly: Action[AnyContent] = silhouette.SecuredAction(WithRole(AdminRole)) { implicit request =>
    Ok("SUCCESS! (only ADMIN)")
  }

  def userOrAdmin: Action[AnyContent] = silhouette.SecuredAction(WithRole(UserRole) || WithRole(AdminRole)) { implicit request =>
    Ok("SUCCESS! (USER or ADMIN)")
  }

  def authenticate(provider: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    providerRegistry.get(provider).map { provider =>
      provider.authenticate().flatMap {
        case Left(result) => Future.successful(result)
        case Right(authInfo: OAuth2Info) => for {
          profile <- provider.retrieveProfile(authInfo)
          user <- userService.save(profile)
          authenticator <- authenticatorService.create(profile.loginInfo)
          value <- authenticatorService.init(authenticator)
          result <- authenticatorService.embed(value, Redirect("http://localhost:4200/"))
          futurePlayer <- battlePlayerService.getBattlePlayer(user.loginInfo.providerKey.toString).map {
            case Some(value) => Future.successful(value)
            case None => battlePlayerService.saveBattlePlayer(
              BattlePlayer(0, user.firstName.getOrElse(UUID.randomUUID().toString), 1400, user.loginInfo.providerKey.toString))
          }
          player <- futurePlayer
          _ <- battlePlayerService.getOrCreateActor(player)
        } yield {
          eventBus.publish(LoginEvent(user, request))
          result.withCookies(Cookie("JWT", value, httpOnly = true, domain = Some("localhost")))
        }
      }
    }.getOrElse(Future.successful(NotFound(s"Provider for $provider not found")))
  }

  def signOut: Action[AnyContent] = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    val opt = for {
      player <- OptionT(battlePlayerService.getBattlePlayer(request.identity.loginInfo.providerKey))
      actor <- OptionT.liftF(battlePlayerService.getOrCreateActor(player))
    } yield actor ! Disconnected()

    opt.value.recover {
      case e => e.printStackTrace(); NotFound
    }
    eventBus.publish(LogoutEvent(request.identity, request))
    authenticatorService.discard(request.authenticator, Ok)
  }
}

trait DefaultEnv extends Env {
  type I = BattleUser
  type A = JWTAuthenticator
}

case class BattleUser(
                       userId: UUID,
                       loginInfo: LoginInfo,
                       firstName: Option[String],
                       lastName: Option[String],
                       fullName: Option[String],
                       email: Option[String],
                       role: Role = UserRole,
                       profilePicUrl: Option[String] = None,
                       avatarPicUrl: Option[String] = None) extends Identity

object Roles {

  sealed abstract class Role(val name: String)

  case object AdminRole extends Role("admin")

  case object UserRole extends Role("user")

  case object Guest extends Role("guest")

}

case class WithRole(role: Role) extends Authorization[BattleUser, DefaultEnv#A] {
  override def isAuthorized[B](user: BattleUser, authenticator: DefaultEnv#A)
                              (implicit request: Request[B]): Future[Boolean] =
    Future.successful(user.role == role)
}
