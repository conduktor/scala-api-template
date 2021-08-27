package io.conduktor.api.repository.db

import eu.timepit.refined.types.string.NonEmptyString
import io.conduktor.primitives.types.UserName
import skunk.{Command, Fragment, Query}
import skunk.codec.all.{text, uuid}
import skunk.implicits.toStringOps

import java.util.UUID

private[db] object Fragments {
  val fullPostFields: Fragment[skunk.Void] = sql"id, title, author, content, published, created_at"
  val byId: Fragment[UUID]                 = sql"where id = $uuid"
  val byTitle: Fragment[NonEmptyString]    = sql"where title = $nonEmptyText"

  def postQuery[A](where: Fragment[A]): Query[A, PostDb] =
    sql"SELECT $fullPostFields FROM post $where".query(PostDb.codec)

  def postDelete[A](where: Fragment[A]): Command[A] =
    sql"DELETE FROM post $where".command

  // using a Query to retrieve user
  def postCreate: Query[(UUID, NonEmptyString, UserName, String), PostDb] =
    sql"""
        INSERT INTO post (id, title, author, content)
        VALUES ($uuid, $nonEmptyText, $usernameCodec, $text)
        RETURNING $fullPostFields
      """
      .query(PostDb.codec)
      .gcontramap[(UUID, NonEmptyString, UserName, String)]
}
