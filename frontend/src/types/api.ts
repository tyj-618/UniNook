export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface SessionUser {
  id: number
  username: string
  nickname: string
  nicknameSetupRequired?: boolean
  avatarUrl: string | null
  role: number
  schoolId?: number | null
  universityId?: number | null
  schoolName?: string | null
  campusName?: string | null
  schoolCity?: string | null
  bio?: string | null
  status?: number
  createdAt?: string
}

export interface LoginResponse {
  token: string
  expiresIn: number
  user: SessionUser
}

export interface RegisterRequest {
  username: string
  password: string
}

export interface RegisterResponse {
  userId: number
  username: string
  nickname: string
}

export interface PageResponse<T> {
  page: number
  size: number
  total: number
  pages: number
  records: T[]
}

export interface PostSchool {
    id: number
    name: string
    campusName: string | null
  city: string | null
}

export interface PostCategory {
  id: number
  name: string
  code: string
}

export interface PostAuthor {
  id: number
  nickname: string
  avatarUrl: string | null
}

export interface PostListItem {
  id: number
  title: string
  summary: string
  school: PostSchool
  category: PostCategory
  author: PostAuthor
  viewCount: number
  likeCount: number
  commentCount: number
  hotScore: number
  createdAt: string
}

export interface PostDetail {
  id: number
  title: string
  content: string
  school: PostSchool
  category: PostCategory
  author: PostAuthor
  viewCount: number
  likeCount: number
  commentCount: number
  liked: boolean
  createdAt: string
  updatedAt: string
}

export type QuestionSourceType = 'POST' | 'COMMENT'
export type QuestionStatus = 'OPEN' | 'COMPLETED'
export type QuestionAnswerStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN'
export type CandidateAnswerAiVerdict = 'RELEVANT' | 'UNCERTAIN' | 'IRRELEVANT'
export type MyQuestionRole = 'ASKED' | 'SUBSCRIBED'

export interface QuestionAnswer {
  id: number
  commentId: number
  postId: number
  parentCommentId: number | null
  answerer: PostAuthor
  content: string
  status: QuestionAnswerStatus
  createdAt: string
  reviewedAt: string | null
}

export interface CandidateAnswerAiReview {
  relevanceScore: number
  verdict: CandidateAnswerAiVerdict
  rationale: string
  modelAssisted: boolean
  requestId: string
}

export interface QuestionTracking {
  id: number
  sourceType: QuestionSourceType
  sourceId: number
  sourcePostId: number
  sourcePreview: string
  asker: PostAuthor
  questionText: string
  status: QuestionStatus
  approvedAnswerCount: number
  approvedAnswers: QuestionAnswer[]
  subscriberCount: number
  subscribed: boolean
  createdAt: string
  updatedAt: string
}

export interface QuestionSourceSummary {
  id: number
  sourceType: QuestionSourceType
  sourceId: number
  sourcePostId: number
  asker: PostAuthor
  questionText: string
  status: QuestionStatus
  approvedAnswerCount: number
  subscriberCount: number
  subscribed: boolean
}

export interface QuestionSubscriptionResponse {
  subscribed: boolean
  subscriberCount: number
}

export interface PostComment {
  id: number
  content: string
  rootCommentId: number | null
  parentCommentId: number | null
  author: PostAuthor & { schoolName: string | null; campusName: string | null }
  replyToUserId: number | null
  replyToNickname: string | null
  likeCount: number
  liked: boolean
  createdAt: string
}

export interface PublicUserProfile {
  id: number
  username: string
  nickname: string
  schoolId: number | null
  universityId: number | null
  schoolName: string | null
  campusName: string | null
  schoolCity: string | null
  avatarUrl: string | null
  bio: string | null
  postCount: number
  commentCount: number
  likeCount: number
  createdAt: string
}

export interface MyComment {
  id: number
  postId: number
  postTitle: string
  content: string
  createdAt: string
}

export interface MyLike {
  targetType: 'POST' | 'COMMENT'
  postId: number
  commentId: number | null
  postTitle: string
  targetContent: string
  createdAt: string
}

export interface CreateCommentResponse {
  commentId: number
}

export interface LikeResponse {
  liked: boolean
  likeCount: number
}

export interface School {
  id: number
  universityId: number | null
  name: string
  campusName: string
  province: string
  city: string
  latitude: number
  longitude: number
  distanceKm: number | null
}

export interface SchoolChangeQuota {
  used: number
  limit: number
  remaining: number
  resetsOn: string
}

export type CampusScope = 'CAMPUS' | 'UNIVERSITY' | 'NEARBY_10' | 'NEARBY_20' | 'CITY'

export interface Category {
  id: number
  name: string
  code: string
  sortOrder: number
}

export interface CreatePostResponse {
  postId: number
}

export interface Notice {
  id: number
  type: number
  content: string
  readStatus: number
  sender: PostAuthor
  postId: number | null
  commentId: number | null
  questionId: number | null
  targetDeleted: boolean
  targetDeletedMessage: string | null
  createdAt: string
}

export interface AiPostReference {
  postId: number
  title: string
  schoolName: string
  excerpt: string
}

export interface PendingPostAction {
  actionId: string
  type: 'CREATE_POST'
  title: string
  content: string
  expiresAt: string
}

export interface AiAssistantResponse {
  answer: string
  references: AiPostReference[]
  insufficientEvidence: boolean
  requestId: string
  pendingAction: PendingPostAction | null
}

export interface AiAssistantStreamMetadata {
  references: AiPostReference[]
  insufficientEvidence: boolean
  pendingAction: PendingPostAction | null
}
