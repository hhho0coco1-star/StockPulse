export interface Post {
  postId: string;
  symbol: string;
  authorId: string;
  authorNickname: string;
  content: string;
  likeCount: number;
  commentCount: number;
  liked: boolean;
  createdAt: string;
}

export interface PostRequest {
  content: string;
}

export interface Comment {
  commentId: string;
  postId: string;
  authorId: string;
  authorNickname: string;
  content: string;
  createdAt: string;
}

export interface CommentRequest {
  content: string;
}

export interface ChatMessage {
  messageId: string;
  symbol: string;
  userId: string;
  nickname: string;
  message: string;
  timestamp: string;
}

export interface ChatSendRequest {
  message: string;
}
