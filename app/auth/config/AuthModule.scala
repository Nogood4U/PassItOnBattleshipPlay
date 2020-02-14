package auth.config


import com.google.inject.{AbstractModule, Provides}
import com.mohiva.play.silhouette.api.crypto.{AuthenticatorEncoder, Base64AuthenticatorEncoder, Signer}
import com.mohiva.play.silhouette.api.repositories.AuthenticatorRepository
import com.mohiva.play.silhouette.api.services.{AuthenticatorService, IdentityService}
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.api.{Environment, EventBus, LoginInfo, Silhouette, SilhouetteProvider}
import com.mohiva.play.silhouette.crypto.{JcaSigner, JcaSignerSettings}
import com.mohiva.play.silhouette.impl.authenticators.{JWTAuthenticator, JWTAuthenticatorService, JWTAuthenticatorSettings}
import com.mohiva.play.silhouette.impl.providers.oauth2.{FacebookProvider, GoogleProvider}
import com.mohiva.play.silhouette.impl.providers.state.{CsrfStateItemHandler, CsrfStateSettings}
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfile, DefaultSocialStateHandler, OAuth2Provider, OAuth2Settings, SocialProfile, SocialStateHandler, oauth2}
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
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

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
    //bind[IdentityService[BattleUser]].to[AuthBattleUserService]
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
                          userService: AuthBattleUserMongoService,
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
  @Named("provider-registry")
  def provideProviderRegistry(socialStateHandler: SocialStateHandler, httpLayer: HTTPLayer, config: Configuration): Map[String, OAuth2Provider] = {

    val googleSettings = config.underlying.as[OAuth2Settings]("silhouette.google")
    val facebookSettings = config.underlying.as[OAuth2Settings]("silhouette.facebook")

    Map(
      GoogleProvider.ID -> new GoogleProvider(httpLayer, socialStateHandler, googleSettings),
      FacebookProvider.ID -> new FacebookProvider(httpLayer, socialStateHandler, facebookSettings)
    )
  }

  type PicProvider = CommonSocialProfile => Option[String]

  @Provides
  @Named("profile-url-registry")
  def provideProviderProfileUrlMap(): Map[String, PicProvider] = Map(
    GoogleProvider.ID -> ((info: CommonSocialProfile) => info.avatarURL.map(_.replace("s100", "s600"))),
    FacebookProvider.ID -> ((info: CommonSocialProfile) => Option(s"https://graph.facebook.com/${info.loginInfo.providerKey}/picture?type=large"))
  )

  @Provides
  def proviceJWTAuthenticatorService(idGenerator: IDGenerator, repo: AuthenticatorRepository[JWTAuthenticator]): AuthenticatorService[JWTAuthenticator] = {
    new CustomJWTAuthenticatorService(JWTAuthenticatorSettings(sharedSecret = "secret01234567890ABCDEFGHIJKLMNO"),
      Some(repo),
      new Base64AuthenticatorEncoder,
      idGenerator, Clock())
  }

  @Provides
  def providesJWTAuthenticatorClassTag: ClassTag[JWTAuthenticator] = ClassTag[JWTAuthenticator](classOf[JWTAuthenticator])
}

class CustomJWTAuthenticatorService(
                                     settings: JWTAuthenticatorSettings,
                                     repository: Option[AuthenticatorRepository[JWTAuthenticator]],
                                     authenticatorEncoder: AuthenticatorEncoder,
                                     idGenerator: IDGenerator,
                                     clock: Clock) extends JWTAuthenticatorService(settings, repository, authenticatorEncoder, idGenerator, clock) {

  override def retrieve[B](implicit request: ExtractableRequest[B]): Future[Option[JWTAuthenticator]] = {

    val cookieFuture: Future[Option[JWTAuthenticator]] = JWTAuthenticator.unserialize(request.cookies.get("JWT").map(_.value).orNull, authenticatorEncoder, settings) match {
      case Success(authenticator) => repository.fold(Future.successful(Option(authenticator)))(_.find(authenticator.id))
      case Failure(e) =>
        logger.info(e.getMessage, e)
        Future.failed(e)
    }
    cookieFuture.recoverWith {
      case _ => super.retrieve
    }(this.executionContext)
  }
}
