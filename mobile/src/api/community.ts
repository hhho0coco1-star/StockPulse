import { apiClient } from './client';
import type { ApiResponse, PageResponse } from '@/types/api';
import type { Post, Comment, PostRequest, CommentRequest } from '@/types/community';

export const communityApi = {
  async getPosts(symbol: string, page = 0): Promise<PageResponse<Post>> {
    const { data } = await apiClient.get<ApiResponse<PageResponse<Post>>>(
      `/community/${symbol}/posts`,
      { params: { page, size: 20 } },
    );
    if (!data.success || !data.data) throw new Error('게시글 조회 실패');
    return data.data;
  },

  async createPost(symbol: string, req: PostRequest): Promise<Post> {
    const { data } = await apiClient.post<ApiResponse<Post>>(
      `/community/${symbol}/posts`,
      req,
    );
    if (!data.success || !data.data) throw new Error('게시글 작성 실패');
    return data.data;
  },

  async getPost(postId: string): Promise<Post> {
    const { data } = await apiClient.get<ApiResponse<Post>>(`/community/posts/${postId}`);
    if (!data.success || !data.data) throw new Error('게시글 조회 실패');
    return data.data;
  },

  async deletePost(postId: string): Promise<void> {
    await apiClient.delete(`/community/posts/${postId}`);
  },

  async getComments(postId: string): Promise<Comment[]> {
    const { data } = await apiClient.get<ApiResponse<Comment[]>>(
      `/community/posts/${postId}/comments`,
    );
    if (!data.success || !data.data) throw new Error('댓글 조회 실패');
    return data.data;
  },

  async createComment(postId: string, req: CommentRequest): Promise<Comment> {
    const { data } = await apiClient.post<ApiResponse<Comment>>(
      `/community/posts/${postId}/comments`,
      req,
    );
    if (!data.success || !data.data) throw new Error('댓글 작성 실패');
    return data.data;
  },

  async toggleLike(postId: string): Promise<{ liked: boolean; likeCount: number }> {
    const { data } = await apiClient.post<
      ApiResponse<{ liked: boolean; likeCount: number }>
    >(`/community/posts/${postId}/like`);
    if (!data.success || !data.data) throw new Error('좋아요 처리 실패');
    return data.data;
  },
};
