import grails.plugins.Plugin
import groovy.util.logging.Slf4j
import org.grails.plugins.springsession.GrailsSessionProxy
import org.grails.plugins.springsession.SpringHttpSession
import org.grails.plugins.springsession.config.SpringSessionConfig
import org.grails.plugins.springsession.data.redis.RedisLogoutHandler
import org.grails.plugins.springsession.data.redis.RedisSecurityContextRepository
import org.grails.plugins.springsession.data.redis.SecurityContextDao
import org.grails.plugins.springsession.data.redis.config.MasterNamedNode
import org.grails.plugins.springsession.data.redis.config.NoOpConfigureRedisAction
import org.grails.plugins.springsession.scope.SpringSessionBeanFactoryPostProcessor
import org.grails.plugins.springsession.web.http.HttpSessionSynchronizer
import org.springframework.data.redis.connection.RedisNode
import org.springframework.data.redis.connection.RedisSentinelConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.web.context.SecurityContextPersistenceFilter
import org.springframework.session.data.redis.RedisFlushMode
import org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration
import org.springframework.session.web.http.CookieHttpSessionStrategy
import org.springframework.session.web.http.DefaultCookieSerializer
import org.springframework.session.web.http.HeaderHttpSessionStrategy
import redis.clients.jedis.JedisShardInfo
import utils.SpringSessionUtils

@Slf4j
class SpringSessionGrailsPlugin extends Plugin {

	def grailsVersion = "3.0.0 > *"
	def title = "Spring Session Grails Plugin"
	def author = "Jitendra Singh"
	def authorEmail = "jeet.mp3@gmail.com"
	def description = 'Provides support for SpringSession project'
	def documentation = "https://github.com/jeetmp3/spring-session"
	def license = "APACHE"
	def issueManagement = [url: "https://github.com/jeetmp3/spring-session/issues"]
	def scm = [url: "https://github.com/jeetmp3/sprinrequest.getSession()g-session"]
	def loadAfter = ['springSecurityCore', 'cors']
	def profiles = ['web']

	Closure doWithSpring() {
		{ ->
			SpringSessionUtils.application = grailsApplication
			ConfigObject conf = SpringSessionUtils.sessionConfig

			if (conf.enabled as Boolean) {
				println "\nConfiguring Spring Session..."

				springSessionConfig(SpringSessionConfig) {
					grailsApplication = grailsApplication
				}

				if (conf.redis.sentinel.master && conf.redis.sentinel.nodes) {
					List<Map> nodes = conf.redis.sentinel.nodes as List<Map>
					masterName(MasterNamedNode) {
						name = conf.redis.sentinel.master
					}
					shardInfo(JedisShardInfo, conf.redis.connectionFactory.hostName, conf.redis.connectionFactory.port) {
						password = conf.redis.sentinel.password ?: null
						timeout = conf.redis.sentinel.timeout ?: 5000
					}
					redisSentinelConfiguration(RedisSentinelConfiguration) {
						master = ref("masterName")
						sentinels = (nodes.collect { new RedisNode(it.host as String, it.port as Integer) }) as Set
					}
					redisConnectionFactory(JedisConnectionFactory, ref("redisSentinelConfiguration"), ref("poolConfig")) {
						shardInfo = ref("shardInfo")
						usePool = conf.redis.connectionFactory.usePool
					}
				} else {
					// Redis Connection Factory Default is JedisConnectionFactory
					redisConnectionFactory(JedisConnectionFactory) {
						hostName = conf.redis.connectionFactory.hostName ?: "localhost"
						port = conf.redis.connectionFactory.port ?: 6379
						timeout = conf.redis.connectionFactory.timeout ?: 2000
						usePool = conf.redis.connectionFactory.usePool
						database = conf.redis.connectionFactory.dbIndex
						if (conf.redis.connectionFactory.password) {
							password = conf.redis.connectionFactory.password
						}
						convertPipelineAndTxResults = conf.redis.connectionFactory.convertPipelineAndTxResults
					}
				}

				sessionRedisTemplate(RedisTemplate) { bean ->
					keySerializer = ref("stringRedisSerializer")
					hashKeySerializer = ref("stringRedisSerializer")
					connectionFactory = ref("redisConnectionFactory")
					if (conf.lazy.deserialization as Boolean) {
						defaultSerializer = ref("lazyDeserializationRedisSerializer")
					} else {
						defaultSerializer = ref("jdkSerializationRedisSerializer")
					}
					bean.initMethod = "afterPropertiesSet"
				}

				String defaultStrategy = conf.strategy.defaultStrategy
				if (defaultStrategy == "HEADER") {
					httpSessionStrategy(HeaderHttpSessionStrategy) {
						headerName = conf.strategy.httpHeader.headerName
					}
				} else {
					cookieSerializer(DefaultCookieSerializer) {
						cookieName = conf.strategy.cookie.name
						cookiePath = conf.strategy.cookie.path
						domainNamePattern = conf.strategy.cookie.domainNamePattern
					}

					httpSessionStrategy(CookieHttpSessionStrategy) {
						cookieSerializer = ref("cookieSerializer")
					}
				}

				redisHttpSessionConfiguration(RedisHttpSessionConfiguration) {
					maxInactiveIntervalInSeconds = conf.maxInactiveInterval
					httpSessionStrategy = ref("httpSessionStrategy")
					redisFlushMode = RedisFlushMode.ON_SAVE
				}

				configureRedisAction(NoOpConfigureRedisAction)
				httpSessionSynchronizer(HttpSessionSynchronizer) {
					persistMutable = conf.allow.persist.mutable as Boolean
				}

				"${conf.beanName}"(SpringHttpSession){
					lazyDeserialization = conf.lazy.deserialization as Boolean
					redisSerializer = ref("lazyDeserializationRedisSerializer")
					sessionProxy = new GrailsSessionProxy()
				}

				if(conf.isolate.securityContext as Boolean){
					securityContextDao(SecurityContextDao){
						redisTemplate = ref("sessionRedisTemplate")
					}

					redisSecurityContextRepository(RedisSecurityContextRepository){
						securityContextDao = ref("securityContextDao")
					}

					redisLogoutHandler(RedisLogoutHandler){
						redisSecurityContextRepository = ref("redisSecurityContextRepository")
					}

					securityContextPersistenceFilter(SecurityContextPersistenceFilter, ref("redisSecurityContextRepository"))
				}

				springSessionBeanFactoryPostProcessor(SpringSessionBeanFactoryPostProcessor)

				println "... finished configuring Spring Session"
			}
		}
	}
}
