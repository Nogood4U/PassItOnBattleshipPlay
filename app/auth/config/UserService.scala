package auth.config

import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfile, SocialProfile}
import controllers.BattleUser
import controllers.Roles.UserRole
import javax.inject.Singleton

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

@Singleton
class UserService extends IdentityService[BattleUser] {
  val data = mutable.Map[String, BattleUser]()

  override def retrieve(loginInfo: LoginInfo): Future[Option[BattleUser]] = Future {
    data.get(loginInfo.providerID)
  }

  def save(profile: CommonSocialProfile) = {
    val user = BattleUser(UUID.randomUUID(),
      profile.loginInfo,
      profile.firstName,
      profile.lastName,
      profile.fullName,
      profile.email,
      None,
      UserRole)
    data.put(profile.loginInfo.providerID, user)
    Future {
      user
    }
  }
}

class UserDAO {

}