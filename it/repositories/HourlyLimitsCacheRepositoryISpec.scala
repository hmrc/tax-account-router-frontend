package repositories

import connector.InternalUserIdentifier
import model.Locations
import org.joda.time.{DateTime, DateTimeUtils}
import org.scalatest.{BeforeAndAfter, GivenWhenThen}
import play.api.libs.json.Reads._
import play.api.libs.json._
import reactivemongo.json.collection.JSONCollection
import uk.gov.hmrc.cache.model.Id
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class HourlyLimitsCacheRepositoryISpec extends UnitSpec with MongoSpecSupport with BeforeAndAfter with WithFakeApplication with GivenWhenThen {

  val repository = new HourlyLimitsCacheRepository

  override def afterAll(): Unit = {
    DateTimeUtils.setCurrentMillisSystem()
    mongo().drop()
  }

  before {
    DateTimeUtils.setCurrentMillisSystem()
    await(mongo().collection[JSONCollection]("hourlyLimits").drop())
  }

  val dataReads = (JsPath \ "users").read[Array[String]].map(users => users.map(InternalUserIdentifier(_)))

  "HourlyLimitsCacheRepository" should {
    "create or update cache documents - no update with same user" in {

      val fixedTime = DateTime.now()
      DateTimeUtils.setCurrentMillisFixed(fixedTime.getMillis)

      Given("a user and a hour")
      val hour = 12
      val location = Locations.BusinessTaxAccount
      val documentId = HourlyLimitId(location, hour)

      val userId = InternalUserIdentifier("user-id")
      val hourlyLimit = 100

      When("createOrUpdate is invoked")
      await(repository.createOrUpdate(documentId, hourlyLimit, userId))

      Then("a new document should be created")
      val documentAfterCreation = await(repository.findById(Id(documentId.value)))
      documentAfterCreation should not be None

      documentAfterCreation.get.id.id shouldBe s"${location.name}-$hour"

      val usersAfterCreation = dataReads.reads(documentAfterCreation.get.data.get)
      usersAfterCreation.get shouldBe Array(userId)

      documentAfterCreation.get.modifiedDetails.createdAt.getMillis shouldBe fixedTime.getMillis
      documentAfterCreation.get.modifiedDetails.lastUpdated.getMillis shouldBe fixedTime.getMillis

      When("createOrUpdate is invoked with the same user in the same hour")
      await(repository.createOrUpdate(documentId, hourlyLimit, userId))

      Then("the existing document should not be updated")
      val documentAfterUpdate = await(repository.findById(Id(documentId.value)))
      documentAfterUpdate should not be None

      documentAfterUpdate.get.id.id shouldBe s"${location.name}-$hour"

      val usersAfterUpdate = dataReads.reads(documentAfterCreation.get.data.get)
      usersAfterUpdate.get shouldBe Array(userId)

      documentAfterUpdate.get.modifiedDetails.createdAt.getMillis shouldBe fixedTime.getMillis
      documentAfterUpdate.get.modifiedDetails.lastUpdated.getMillis shouldBe fixedTime.getMillis
    }

    "create or update cache documents - update with different users" in {

      val fixedTime1 = DateTime.now()
      DateTimeUtils.setCurrentMillisFixed(fixedTime1.getMillis)

      Given("a user and a hour")
      val hour = 12
      val location = Locations.BusinessTaxAccount
      val documentId = HourlyLimitId(location, hour)

      val user1Id = InternalUserIdentifier("user-1-id")
      val hourlyLimit = 100

      When("createOrUpdate is invoked")
      await(repository.createOrUpdate(documentId, hourlyLimit, user1Id))

      Then("a new document should be created")
      val documentAfterCreation = await(repository.findById(Id(documentId.value)))
      documentAfterCreation should not be None

      documentAfterCreation.get.id.id shouldBe s"${location.name}-$hour"

      val usersAfterCreation = dataReads.reads(documentAfterCreation.get.data.get)
      usersAfterCreation.get shouldBe Array(user1Id)

      When("createOrUpdate is invoked with another user in the same hour and the hourly limit has not been reached")
      val user2Id = InternalUserIdentifier("user-2-id")
      val fixedTime2 = fixedTime1.plusHours(1)
      DateTimeUtils.setCurrentMillisFixed(fixedTime2.getMillis)
      await(repository.createOrUpdate(documentId, hourlyLimit, user2Id))

      Then("the existing document should be updated")
      val documentAfterUpdate = await(repository.findById(Id(documentId.value)))
      documentAfterUpdate should not be None

      documentAfterUpdate.get.id.id shouldBe s"${location.name}-$hour"

      val usersAfterUpdate = dataReads.reads(documentAfterUpdate.get.data.get)
      usersAfterUpdate.get shouldBe Array(user1Id, user2Id)

      documentAfterUpdate.get.modifiedDetails.createdAt.getMillis shouldBe fixedTime1.getMillis
      documentAfterUpdate.get.modifiedDetails.lastUpdated.getMillis shouldBe fixedTime2.getMillis
    }

    "update the document if the number of users per hour has not been reached the limit" in {

      Given("a user and a hour")
      val hour = 12
      val location = Locations.BusinessTaxAccount
      val documentId = HourlyLimitId(location, hour)

      val user1Id = InternalUserIdentifier("user-1-id")

      And("a hourly limit of 1")
      val hourlyLimit = 1

      And("a cache object for that hour with that user")
      await(repository.createOrUpdate(documentId, hourlyLimit, user1Id))

      When("createOrUpdate is invoked with an another user")
      val user2Id = InternalUserIdentifier("user-2-id")
      await(repository.createOrUpdate(documentId, hourlyLimit, user2Id))

      Then("the document should contain only the first user")
      val documentAfterCreation = await(repository.findById(Id(documentId.value)))
      documentAfterCreation should not be None

      documentAfterCreation.get.id.id shouldBe s"${location.name}-$hour"

      val users = dataReads.reads(documentAfterCreation.get.data.get)
      users.get shouldBe Array(user1Id)
    }

    "state whether a user exists inside a given cache document" in {

      Given("a user and a hour")
      val hour = 12
      val location = Locations.BusinessTaxAccount
      val documentId = HourlyLimitId(location, hour)

      val userId = InternalUserIdentifier("user-id")
      val hourlyLimit = 100

      And("a user existing in a given cache document")
      await(repository.createOrUpdate(documentId, hourlyLimit, userId))

      When("the existing user is checked to be existing in the cache document")
      val existingUserExists = await(repository.exists(documentId, userId))

      Then("the existing user should exist")
      existingUserExists shouldBe true

      When("another user is checked to be existing in the existing cache document")
      val otherUserExists = await(repository.exists(documentId, "other-id"))

      Then("the user should not be existing")
      otherUserExists shouldBe false

      When("the existing user is checked to be existing in a non existing cache document")
      val existingUserExistsInNonExistingDocument = await(repository.exists(HourlyLimitId(location, hour + 1), userId))

      Then("the user should not be existing")
      existingUserExistsInNonExistingDocument shouldBe false
    }
  }
}
