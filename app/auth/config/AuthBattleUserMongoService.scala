package auth.config

import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfile, SocialProfile}
import controllers.Roles.{AdminRole, Guest, Role, UserRole}
import controllers.{BattleUser, Roles}
import javax.inject.{Inject, Named}
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

class AuthBattleUserMongoService @Inject()(@Named("profile-url-registry")
                                           profileUrlProviderRegistry: Map[String, CommonSocialProfile => Option[String]],
                                           val reactiveMongoApi: ReactiveMongoApi)(implicit executionContext: ExecutionContext) extends IdentityService[BattleUser] {
  implicit val roleReads = new Reads[Roles.Role] {
    override def reads(json: JsValue): JsResult[Role] = json.asOpt[String] match {
      case Some(value) if value == "user" => JsSuccess(UserRole)
      case Some(value) if value == "admin" => JsSuccess(AdminRole)
      case None => JsSuccess(Guest)
    }
  }

  implicit val roleWrites = new Writes[Roles.Role] {
    override def writes(o: Role): JsValue = JsString(o.name)
  }

  implicit val userReads = Json.reads[BattleUser]

  implicit val userWrites = Json.writes[BattleUser]

  private def usersF = reactiveMongoApi.database.map(_.collection[JSONCollection]("users"))

  override def retrieve(loginInfo: LoginInfo): Future[Option[BattleUser]] = {
    for {
      users <- usersF
      user <- users.find(Json.obj("loginInfo.providerKey" -> loginInfo.providerKey), projection = Option.empty[BattleUser]).one[BattleUser]
    } yield user
  }


  def save(profile: SocialProfile) = {
    val user = profile match {
      case commonSocialProfile: CommonSocialProfile =>
        BattleUser(UUID.randomUUID(),
          profile.loginInfo,
          commonSocialProfile.firstName,
          commonSocialProfile.lastName,
          commonSocialProfile.fullName,
          commonSocialProfile.email,
          UserRole,
          profileUrlProviderRegistry.get(profile.loginInfo.providerID).flatMap(_.apply(commonSocialProfile)),
          commonSocialProfile.avatarURL)
      case _ =>
        BattleUser(UUID.randomUUID(),
          profile.loginInfo,
          null,
          null,
          null,
          null,
          UserRole)
    }
    for {
      users <- usersF
      _ <- users.insert(ordered = false).one[BattleUser](user)
    } yield user
  }
}
