package auth.config

import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfile, SocialProfile}
import controllers.BattleUser
import controllers.Roles.UserRole
import javax.inject.{Inject, Named, Singleton}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

@Singleton
class AuthBattleUserService @Inject()(@Named("profile-url-registry")
                                      profileUrlProviderRegistry: Map[String, CommonSocialProfile => Option[String]]) extends IdentityService[BattleUser] {

  val data = mutable.Map[String, BattleUser]()

  override def retrieve(loginInfo: LoginInfo): Future[Option[BattleUser]] = Future {
    data.get(loginInfo.providerID)
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
    data.put(profile.loginInfo.providerID, user)
    Future {
      user
    }
  }
}
