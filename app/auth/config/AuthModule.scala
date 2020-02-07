package auth.config


import com.google.inject.{AbstractModule, Provides}
import com.mohiva.play.silhouette.api.crypto.{Base64AuthenticatorEncoder, Signer}
import com.mohiva.play.silhouette.api.repositories.AuthenticatorRepository
import com.mohiva.play.silhouette.api.services.{AuthenticatorService, IdentityService}
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.api.{Environment, EventBus, Silhouette, SilhouetteProvider}
import com.mohiva.play.silhouette.crypto.{JcaSigner, JcaSignerSettings}
import com.mohiva.play.silhouette.impl.authenticators.{JWTAuthenticator, JWTAuthenticatorService, JWTAuthenticatorSettings}
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider
import com.mohiva.play.silhouette.impl.providers.state.{CsrfStateItemHandler, CsrfStateSettings}
import com.mohiva.play.silhouette.impl.providers.{DefaultSocialStateHandler, OAuth2Settings, SocialStateHandler}
import com.mohiva.play.silhouette.impl.util.{DefaultFingerprintGenerator, PlayCacheLayer, SecureRandomIDGenerator}
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.repositories.CacheAuthenticatorRepository
import com.typesafe.config.Config
import controllers.{BattleUser, DefaultEnv}
import javax.inject.Named
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration
import play.api.libs.ws.WSClient
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import play.api.mvc.Cookie

import scala.concurrent.ExecutionContext.Implicits._
import scala.reflect.ClassTag

class AuthModule extends AbstractModule with ScalaModule {

  implicit val sameSiteReader: ValueReader[Option[Option[Cookie.SameSite]]] =
    (config: Config, path: String) => {
      if (config.hasPathOrNull(path)) {
        if (config.getIsNull(path))
          Some(None)
        else {
          Some(Cookie.SameSite.parse(config.getString(path)))
        }
      } else {
        None
      }
    }

  override def configure() {
    bind[Silhouette[DefaultEnv]].to[SilhouetteProvider[DefaultEnv]]
    bind[IdentityService[BattleUser]].to[UserService]
    bind[IDGenerator].toInstance(new SecureRandomIDGenerator())
    bind[PasswordHasher].toInstance(new BCryptPasswordHasher)
    bind[FingerprintGenerator].toInstance(new DefaultFingerprintGenerator(false))
    bind[EventBus].toInstance(EventBus())
    bind[CacheLayer].to[PlayCacheLayer]
    bind[AuthenticatorRepository[JWTAuthenticator]].to[CacheAuthenticatorRepository[JWTAuthenticator]]
  }

  @Provides
  def provideHTTPLayer(client: WSClient): HTTPLayer = new PlayHTTPLayer(client)

  @Provides
  def provideSocialStateHandler(@Named("battle-handler") csrfStateItemHandler: CsrfStateItemHandler,
                                signer: Signer): SocialStateHandler =
    new DefaultSocialStateHandler(Set(csrfStateItemHandler), signer: Signer)

  @Provides
  def providesSigner: Signer = new JcaSigner(JcaSignerSettings("secret01234567890ABCDEFGHIJKLMNO"))

  @Provides
  @Named("battle-handler")
  def providesCsrfStateItemHandler(idGenerator: IDGenerator, signer: Signer, config: Configuration): CsrfStateItemHandler = {
    val settings = config.underlying.as[CsrfStateSettings]("silhouette.csrfStateItemHandler")
    new CsrfStateItemHandler(settings, idGenerator, signer)
  }

  @Provides
  def provideEnvironment(
                          userService: UserService,
                          authenticatorService: AuthenticatorService[JWTAuthenticator],
                          eventBus: EventBus,
                        ): Environment[DefaultEnv] = {

    Environment[DefaultEnv](
      userService,
      authenticatorService,
      Seq(),
      eventBus
    )
  }


  @Provides
  def provideGoogleProvider(socialStateHandler: SocialStateHandler, httpLayer: HTTPLayer, config: Configuration): GoogleProvider = {
    val settings = OAuth2Settings(
      authorizationURL = config.getOptional[String]("silhouette.google.authorizationURL"),
      accessTokenURL = config.getOptional[String]("silhouette.google.accessTokenURL").get,
      redirectURL = config.getOptional[String]("silhouette.google.redirectURL"),
      clientID = config.getOptional[String]("silhouette.google.clientID").get,
      clientSecret = config.getOptional[String]("silhouette.google.clientSecret").get,
      scope = config.getOptional[String]("silhouette.google.scope"))

    new GoogleProvider(httpLayer, socialStateHandler, settings)
  }

  @Provides
  def proviceJWTAuthenticatorService(idGenerator: IDGenerator, repo: AuthenticatorRepository[JWTAuthenticator]): AuthenticatorService[JWTAuthenticator] = {
    new JWTAuthenticatorService(JWTAuthenticatorSettings(sharedSecret = "secret01234567890ABCDEFGHIJKLMNO"),
      Some(repo),
      new Base64AuthenticatorEncoder,
      idGenerator, Clock())
  }

  @Provides
  def providesJWTAuthenticatorClassTag: ClassTag[JWTAuthenticator] = ClassTag[JWTAuthenticator](classOf[JWTAuthenticator])
}
