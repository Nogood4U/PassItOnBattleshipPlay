package controllers

import java.util.UUID

import auth.config.UserService
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.api.{Authorization, Env, EventBus, Identity, LoginEvent, LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfile, OAuth2Info}
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider
import controllers.Roles.{AdminRole, Role, UserRole}
import javax.inject._
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents,
                               silhouette: Silhouette[DefaultEnv],
                               googleProvider: GoogleProvider,
                               userService: UserService,
                               eventBus: EventBus,
                               authenticatorService: AuthenticatorService[JWTAuthenticator]) extends AbstractController(cc) {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    Future {
      Ok(views.html.index("Your new application is ready."))
    }
  }

  def adminOnly = silhouette.SecuredAction(WithRole(AdminRole)) { implicit request =>
    Ok("SUCCESS! (only ADMIN)")
  }

  def userOrAdmin = silhouette.SecuredAction(WithRole(UserRole) || WithRole(AdminRole)) { implicit request =>
    Ok("SUCCESS! (USER or ADMIN)")
  }

  def authenticate(provider: String) = Action.async { implicit request: Request[AnyContent] =>
    googleProvider.authenticate().flatMap {
      case Left(result) => Future.successful(result)
      case Right(authInfo: OAuth2Info) => for {
        profile <- googleProvider.retrieveProfile(authInfo)
        user <- userService.save(profile)
        authenticator <- authenticatorService.create(profile.loginInfo)
        value <- authenticatorService.init(authenticator)
        result <- authenticatorService.embed(value, Redirect(routes.HomeController.index()))
      } yield {
        eventBus.publish(LoginEvent(user, request))
        result
      }
    }
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
                       passwordInfo: Option[PasswordInfo],
                       role: Role = UserRole) extends Identity

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