package com.sparta.hanghaememo.service;

import com.sparta.hanghaememo.dto.MemoDto.MemoLikeResponseDto;
import com.sparta.hanghaememo.dto.MemoDto.MemoRequestDto;
import com.sparta.hanghaememo.dto.MemoDto.MemoResponseDto;
import com.sparta.hanghaememo.dto.ResponseDto;
import com.sparta.hanghaememo.entity.*;
import com.sparta.hanghaememo.exception.ErrorCode;
import com.sparta.hanghaememo.exception.RestApiException;
import com.sparta.hanghaememo.jwt.JwtUtil;
import com.sparta.hanghaememo.repository.CommentLikeRepository;
import com.sparta.hanghaememo.repository.CommentRepository;
import com.sparta.hanghaememo.repository.MemoLikeRepository;
import com.sparta.hanghaememo.repository.MemoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoService {
    private final MemoRepository memoRepository;                                                        // memo repo connect
    private final CommentRepository commentRepository;                                                  // comment repo connect
    private final CommentLikeRepository commentLikeRepository;                                          // commentLike repo connect
    private final MemoLikeRepository memoLikeRepository;                                                // memoLike repo connect
    private final JwtUtil jwtUtil;

    // Memo Create function
    public  MemoResponseDto createMemo(MemoRequestDto requestDto, User user){
        //1. create memo Object and insert DB
        Memo memo = new Memo(requestDto, user.getUsername(),user.getPassword(), user);                  // DTO -> Entity
        memoRepository.save(memo);                                                                      // DB Save
        return new MemoResponseDto(memo);                                                               // return Response  Entity -> DTO
    }

    // Get memos from DB (all)
    public List<MemoResponseDto> getMemos() {
        // 1. Select All Memo
        List<Memo> ListMemo = memoRepository.findAllByOrderByModifiedAtDesc();                          // Select All

        List<MemoResponseDto> ListResponseDto = new ArrayList<>();
        for(Memo memo: ListMemo){
            List<Comment> comments = commentRepository.findAllByMemo(memo);

            if(comments.isEmpty()){
                ListResponseDto.add(new MemoResponseDto(memo));
            }else{
                List<Comment> no_parent_comment = new ArrayList<>();
                for(Comment comment : comments){
                    if(comment.getParent() == null){
                        no_parent_comment.add(comment);
                    }
                }
                ListResponseDto.add(new MemoResponseDto(memo,(ArrayList<Comment>) no_parent_comment));
            }
        }
        return ListResponseDto;
    }

    // Get memo from DB (one)
    public MemoResponseDto getMemo(long id){
        Memo memo = memoRepository.findById(id).orElseThrow(()->                                        // Select one
                new RestApiException(ErrorCode.NOT_FOUND_MEMO)
        );
        List<Comment> comment = commentRepository.findAllByMemo(memo);
        if(comment.isEmpty()){
            return new MemoResponseDto(memo);                                                          // Entity -> DTO
        }else{
            return new MemoResponseDto(memo, (ArrayList<Comment>) comment);
        }
    }

    // DB update function
    @Transactional
    public MemoResponseDto update(Long id, MemoRequestDto requestDto, User user) {
        // 1. 유저 권한 GET
        UserRoleEnum userRoleEnum = user.getRole();
        Memo memo;

        // 2. 유저 권한에 따른 동작 방식 결정
        if(userRoleEnum == UserRoleEnum.USER){
            memo = memoRepository.findById(id).orElseThrow(                                             // find memo
                    () -> new RestApiException(ErrorCode.NOT_FOUND_MEMO)
            );

            if(memo.getUser().getId().equals(user.getId())){                                            // 자기 자신이 작성한 게시물이면
                memo.update(requestDto);                                                                // DB Update
            }else{
                throw new RestApiException(ErrorCode.NOT_FOUND_AUTHORITY);
            }
        }else{                                                                                          // 관리자 권한일 때,
            memo = memoRepository.findById(id).orElseThrow(                                             // find memo
                    () -> new RestApiException(ErrorCode.NOT_FOUND_MEMO)
            );
            memo.update(requestDto);
        }
        return new MemoResponseDto(memo);
    }

    // DB delete function (data delete)

    public ResponseDto deleteMemo(Long id, User user) {
        // 1. 유저 권한 GET
        UserRoleEnum userRoleEnum = user.getRole();
        Memo memo;

        // 2. 유저 권한에 따른 동작 방식 결정
        if(userRoleEnum == UserRoleEnum.USER){
            memo = memoRepository.findById(id).orElseThrow(                                             // find memo
                    () -> new RestApiException(ErrorCode.NOT_FOUND_ID)
            );

            if(memo.getUser().getId().equals(user.getId())){                                            // 자기 자신이 작성한 게시물이면
                memoLikeRepository.deleteAllByMemo(memo);                                               // DB DELETE (선행)
                List<Comment> commentList = commentRepository.findAllByMemo(memo);
                for(Comment comment: commentList){
                    commentLikeRepository.deleteAllByComment(comment);
                }

                commentRepository.deleteAllByMemo(memo);
                memoRepository.deleteById(id);                                                          // 해당 게시물 삭제
            }else{
                throw new RestApiException(ErrorCode.NOT_FOUND_AUTHORITY_DELETE);
            }
        }else{                                                                                          // 관리자 권한일 떄
            memo = memoRepository.findById(id).orElseThrow(                                             // find memo
                    () -> new RestApiException(ErrorCode.NOT_FOUND_ID)
            );
            memoLikeRepository.deleteAllByMemo(memo);
            List<Comment> commentList = commentRepository.findAllByMemo(memo);
            for(Comment comment: commentList){
                commentLikeRepository.deleteAllByComment(comment);
            }

            commentRepository.deleteAllByMemo(memo);
            memoRepository.deleteById(id);
        }
        return  new ResponseDto("삭제 성공", HttpStatus.OK.value());
    }

    @Transactional
    // DB insert (MemoLike)
    public MemoLikeResponseDto memoLike(Long id, User user) {
        // 1. Select Memo
        Memo memo = memoRepository.findById(id).orElseThrow(
                () -> new RestApiException(ErrorCode.NOT_FOUND_MEMO)
        );

        // 2.MemoLike repo에서 좋아요 현황 check
            if(memoLikeRepository.findByMemoAndUser(memo, user).isPresent()){                                   // MemoLike repo check
            // 3. DB Delete
            memoLikeRepository.deleteByMemoAndUser(memo, user);                                             // DB DELETE

            // 4. DB update (Memo count)
            memo.update_count(memo.getCount() - 1);
        }else{
            // 3. DB insert
            memoLikeRepository.save(new MemoLike(memo, user));                                              // DB Save
            // 4. DB update (Memo count)
            memo.update_count(memo.getCount() + 1);                                                         // DB update
        }
        return  new MemoLikeResponseDto(memo.getCount());
    }
}
