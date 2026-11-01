package com.youngmin.board.comment;

import com.youngmin.board.comment.dto.CommentRequest;
import com.youngmin.board.comment.dto.CommentResponse;
import com.youngmin.board.common.exception.BusinessException;
import com.youngmin.board.common.exception.ErrorCode;
import com.youngmin.board.member.MemberRepository;
import com.youngmin.board.post.Post;
import com.youngmin.board.post.PostRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    /**
     * 게시글의 댓글 목록을 "댓글 → 대댓글" 구조로 만든다.
     * 쿼리는 1번(댓글+작성자 fetch join)이고, 부모-자식 묶기는 메모리에서 한다.
     * 댓글마다 대댓글을 따로 조회하면 댓글 수만큼 쿼리가 더 나간다 (N+1).
     *
     * 소프트 삭제된 댓글 표시 규칙
     * - 삭제된 대댓글: 안 보인다.
     * - 삭제된 댓글: 살아 있는 대댓글이 있으면 "삭제된 댓글입니다"로 자리를 지키고, 없으면 안 보인다.
     */
    public List<CommentResponse> getComments(Long postId) {
        List<Comment> comments = commentRepository.findAllWithAuthorByPostId(postId);

        Map<Long, CommentResponse> parents = new LinkedHashMap<>();
        for (Comment comment : comments) {
            if (!comment.isReply()) {
                parents.put(comment.getId(), CommentResponse.from(comment));
            }
        }
        for (Comment comment : comments) {
            if (comment.isReply() && !comment.isDeleted()) {
                // getParent().getId(): 프록시에서 id만 꺼내므로 부모 댓글을 조회하는 쿼리가 나가지 않는다
                CommentResponse parent = parents.get(comment.getParent().getId());
                if (parent != null) {
                    parent.getReplies().add(CommentResponse.from(comment));
                }
            }
        }
        return parents.values().stream()
                .filter(parent -> !parent.isDeleted() || !parent.getReplies().isEmpty())
                .toList();
    }

    @Transactional
    public Long create(Long postId, CommentRequest request, Long memberId) {
        Post post = postRepository.findVisibleById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Comment parent = request.getParentId() == null ? null : findComment(request.getParentId());

        Comment comment = Comment.create(
                request.getContent(), memberRepository.getReferenceById(memberId), post, parent);
        return commentRepository.save(comment).getId();
    }

    /** @return 댓글이 달린 게시글 id (수정 후 돌아갈 곳) */
    @Transactional
    public Long update(Long commentId, CommentRequest request, Long memberId) {
        Comment comment = findCommentOnVisiblePost(commentId);
        validateAuthor(comment, memberId);
        comment.update(request.getContent()); // save() 없음: 변경 감지
        return comment.getPost().getId();
    }

    /**
     * 댓글 삭제 = 소프트 삭제 (deleted = true). 행은 항상 남는다.
     * 대댓글이 있으면 화면에 "삭제된 댓글입니다"로 남고, 대댓글은 그대로 보인다 (getComments 참고).
     * 실제로 지우지 않으므로 대댓글의 부모(FK)가 사라질 일이 없다.
     *
     * @return 댓글이 달린 게시글 id
     */
    @Transactional
    public Long delete(Long commentId, Long memberId) {
        Comment comment = findCommentOnVisiblePost(commentId);
        validateAuthor(comment, memberId);
        if (comment.isDeleted()) {
            throw new BusinessException(ErrorCode.DELETED_COMMENT);
        }
        comment.markDeleted(); // 변경 감지로 UPDATE comments SET deleted = true
        return comment.getPost().getId();
    }

    public Long getPostId(Long commentId) {
        return findComment(commentId).getPost().getId();
    }

    /** 삭제/숨김 글의 댓글은 고치거나 지울 수 없다 */
    private Comment findCommentOnVisiblePost(Long commentId) {
        Comment comment = findComment(commentId);
        if (!comment.getPost().isVisible()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return comment;
    }

    private Comment findComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private void validateAuthor(Comment comment, Long memberId) {
        if (!comment.isWrittenBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR);
        }
    }
}
