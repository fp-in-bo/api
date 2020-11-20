package api.model

case class Event(
  id: Int,
  title: String,
  speaker: String,
  imageUrl: String,
  videoUrl: String,
  description: String
)
