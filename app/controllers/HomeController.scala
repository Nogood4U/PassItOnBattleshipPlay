package controllers

import java.util.UUID

import auth.config.AuthBattleUserService
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import services.BattlePlayerService
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator
import com.mohiva.play.silhouette.impl.providers.{OAuth2Info, OAuth2Provider}
import controllers.Roles.{AdminRole, Role, UserRole}
import javax.inject._
import models.{BattlePlayer, Tables}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
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
                               userService: AuthBattleUserService,
                               eventBus: EventBus,
                               battlePlayerService: BattlePlayerService,
                               authenticatorService: AuthenticatorService[JWTAuthenticator]) extends AbstractController(cc) {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */

  implicit val roleWrites = new Writes[Roles.Role] {
    override def writes(o: Role): JsValue = JsString(o.name)
  }
  implicit val userWrites = Json.writes[BattleUser]
  implicit val playerWrites = Json.writes[BattlePlayer]

  def index: Action[AnyContent] = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    battlePlayerService.getBattlePlayer(request.identity.userId.toString)
      .map(player => {
        Ok(Json.obj("user" -> Json.toJson(request.identity.asInstanceOf[BattleUser]), "player" -> Json.toJson(player)))
      }).recoverWith {
      case _ => Future.successful(NotFound)
    }
  }

  def updatePlayer: Action[AnyContent] = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    battlePlayerService.getBattlePlayer(request.identity.userId.toString)
      .map(player => {
        Ok(Json.obj("user" -> Json.toJson(request.identity.asInstanceOf[BattleUser]), "player" -> Json.toJson(player)))
      }).recoverWith {
      case _ => Future.successful(NotFound)
    }
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
          _ <- battlePlayerService.saveBattlePlayer(BattlePlayer(0, user.firstName.getOrElse(UUID.randomUUID().toString), 1400, user.userId.toString))
        } yield {
          eventBus.publish(LoginEvent(user, request))
          //looks weird af
          result.withCookies(Cookie("JWT", value, httpOnly = true, domain = Some("localhost")))
        }
      }
    }.getOrElse(Future.successful(NotFound(s"Provider for $provider not found")))
  }

  def signOut: Action[AnyContent] = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    val result = Ok("")
    eventBus.publish(LogoutEvent(request.identity, request))
    authenticatorService.discard(request.authenticator, result)
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

}

case class WithRole(role: Role) extends Authorization[BattleUser, DefaultEnv#A] {
  override def isAuthorized[B](user: BattleUser, authenticator: DefaultEnv#A)
                              (implicit request: Request[B]): Future[Boolean] =
    Future.successful(user.role == role)
}
