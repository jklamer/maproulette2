package org.maproulette.controllers.api

import javax.inject.Inject

import org.maproulette.actions.{ActionManager, Actions, ChallengeType, TaskViewed}
import org.maproulette.controllers.ParentController
import org.maproulette.models.dal.{ChallengeDAL, SurveyDAL, TagDAL, TaskDAL}
import org.maproulette.models.{Challenge, Survey, Task}
import org.maproulette.session.{SearchParameters, SessionManager, User}
import play.api.libs.json.{JsValue, Json, Reads, Writes}
import play.api.mvc.Action

/**
  * The challenge controller handles all operations for the Challenge objects.
  * This includes CRUD operations and searching/listing.
  * See {@link org.maproulette.controllers.ParentController} for more details on parent object operations
  * See {@link org.maproulette.controllers.CRUDController} for more details on CRUD object operations
  *
  * @author cuthbertm
  */
class ChallengeController @Inject() (override val childController:TaskController,
                                     override val sessionManager: SessionManager,
                                     override val actionManager: ActionManager,
                                     override val dal: ChallengeDAL,
                                     surveyDAL: SurveyDAL,
                                     taskDAL: TaskDAL,
                                     override val tagDAL: TagDAL)
  extends ParentController[Challenge, Task] with TagsMixin[Challenge] {

  // json reads for automatically reading Challenges from a posted json body
  override implicit val tReads: Reads[Challenge] = Challenge.challengeReads
  // json writes for automatically writing Challenges to a json body response
  override implicit val tWrites: Writes[Challenge] = Challenge.challengeWrites
  // json writes for automatically writing surveys to a json body response
  implicit val sWrites:Writes[Survey] = Survey.surveyWrites
  // json writes for automatically writing Tasks to a json body response
  override protected val cWrites: Writes[Task] = Task.taskWrites
  // json reads for automatically reading tasks from a posted json body
  override protected val cReads: Reads[Task] = Task.taskReads
  // The type of object that this controller deals with.
  override implicit val itemType = ChallengeType()

  override def dalWithTags = dal

  /**
    * Function can be implemented to extract more information than just the default create data,
    * to build other objects with the current object at the core. No data will be returned from this
    * function, it purely does work in the background AFTER creating the current object
    *
    * @param body          The Json body of data
    * @param createdObject The object that was created by the create function
    * @param user          The user that is executing the function
    */
  override def extractAndCreate(body: JsValue, createdObject: Challenge, user: User): Unit = {
    super.extractAndCreate(body, createdObject, user)
    extractTags(body, createdObject, user)
  }

  /**
    * Gets a json list of tags of the challenge
    *
    * @param id The id of the challenge containing the tags
    * @return The html Result containing json array of tags
    */
  def getTagsForChallenge(implicit id: Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(Challenge(id, "", None, -1, "").tags))
    }
  }

  /**
    * Only slightly different from the base read function, if it detects that this is a survey
    * get the answers for the survey and wrap it up in a Survey object
    *
    * @param id The id of the object that is being retrieved
    * @return 200 Ok, 204 NoContent if not found
    */
  def getChallenge(implicit id:Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      dal.retrieveById match {
        case Some(value) =>
          if (value.challengeType == Actions.ITEM_TYPE_SURVEY) {
            val answers = surveyDAL.getAnswers(value.id)
            Ok(Json.toJson(Survey(value, answers)))
          } else {
            Ok(Json.toJson(value))
          }
        case None =>
          NoContent
      }
    }
  }

  /**
    * Gets a random task that is a child of the challenge.
    *
    * @param challengeId The challenge id that is the parent of the tasks that you would be searching for.
    * @param taskSearch Filter based on the name of the task
    * @param tags A comma separated list of tags that optionally can be used to further filter the tasks
    * @param limit Limit of how many tasks should be returned
    * @return A list of Tasks that match the supplied filters
    */
  def getRandomTasks(challengeId: Long,
                     taskSearch:String,
                     tags: String,
                     limit:Int) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      val params = SearchParameters(
        challengeId = Some(challengeId),
        taskSearch = taskSearch,
        taskTags = tags.split(",").toList
      )
      val result = taskDAL.getRandomTasks(User.userOrMocked(user), params, limit)
      result.foreach(task => actionManager.setAction(user, itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }

  /**
    * Gets the featured challenges
    *
    * @param limit The number of challenges to get
    * @param offset The offset
    * @return A Json array with the featured challenges
    */
  def getFeaturedChallenges(limit:Int, offset:Int) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(dal.getFeaturedChallenges(limit, offset)))
    }
  }
}
