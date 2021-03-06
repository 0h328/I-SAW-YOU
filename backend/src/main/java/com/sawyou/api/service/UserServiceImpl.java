package com.sawyou.api.service;

import com.sawyou.api.request.UserUpdateInfoReq;
import com.sawyou.api.response.UserListRes;
import com.sawyou.api.response.UserRes;
import com.sawyou.db.entity.*;
import com.sawyou.db.repository.*;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sawyou.api.request.UserRegisterPostReq;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 유저 관련 비즈니스 로직 처리를 위한 서비스 구현 정의.
 */
@Service("userService")
public class UserServiceImpl implements UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRepositorySupport userRepositorySupport;

    @Autowired
    private FollowingRepository followingRepository;

    @Autowired
    private FollowingRepositorySupport followingRepositorySupport;

    @Autowired
    private FollowerRepository followerRepository;

    @Autowired
    private FollowerRepositorySupport followerRepositorySupport;

    @Autowired
    private CommentLikeRepository commentLikeRepository;

    @Autowired
    private CommentLikeRepositorySupport commentLikeRepositorySupport;

    @Autowired
    private CommentRepositorySupport commentRepositorySupport;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private PostLikeRepositorySupport postLikeRepositorySupport;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostRepositorySupport postRepositorySupport;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 회원가입
    @Override
    public User createUser(UserRegisterPostReq userRegisterInfo) {
        User user = User.builder()
                .userId(userRegisterInfo.getUserId())
                // 보안을 위해서 유저 패스워드 암호화 하여 디비에 저장.
                .userPwd(passwordEncoder.encode(userRegisterInfo.getUserPwd()))
                .userName(userRegisterInfo.getUserName())
                .userEmail(userRegisterInfo.getUserEmail())
                .build();
        
        return userRepository.save(user);
    }

    // 유저 id로 유저 조회
    @Override
    public User getUserByUserId(String userId) {
        // 디비에 유저 정보 조회 (userId로 조회).
        Optional<User> user = userRepositorySupport.findUserByUserId(userId);
        if(!user.isPresent())
            return null;

        return user.get();
    }

    // 유저 seq로 유저 조회 (조회할 유저의 userSeq, 나의 userSeq) - 나와 상대방 사이의 관계 파악
    @Override
    public UserRes getUser(Long userSeq, Long fromSeq) {
        Optional<User> oUser = userRepositorySupport.findUserByUserSeq(userSeq);

        if(!oUser.isPresent())
            return null;

        User user = oUser.get();

        if(user.isUserIsDelete())
            return null;

        boolean isFollowing = false;
        if(followingRepositorySupport.findFollowingByUserSeqAndFromSeq(userSeq, fromSeq).isPresent())
            isFollowing = true;

        List<Following> following = followingRepository.findByUser_UserSeq(userSeq);
        List<Follower> follower = followerRepository.findByFollowerFromSeq(userSeq);
        List<Post> post = postRepository.findByUser_UserSeqAndPostIsDeleteIsFalseOrderByPostWritingTimeDesc(userSeq);

        return UserRes.builder()
                .userSeq(user.getUserSeq())
                .userId(user.getUserId())
                .userName(user.getUserName())
                .userEmail(user.getUserEmail())
                .userDesc(user.getUserDesc())
                .userProfile(user.getUserProfile())
                .postCnt(post.size())
                .followingCnt(following.size())
                .followerCnt(follower.size())
                .isFollowing(isFollowing)
                .build();
    }

    // 유저 정보 수정
    @Override
    @Transactional
    public User updateUserInfo(UserUpdateInfoReq updateInfo, Long userSeq) {
        User user = userRepositorySupport.findUserByUserSeq(userSeq).get();

        if(StringUtils.hasText(updateInfo.getUserId()))
            user.setUserId(updateInfo.getUserId());

        if(StringUtils.hasText(updateInfo.getUserName()))
            user.setUserName(updateInfo.getUserName());

        if(StringUtils.hasText(updateInfo.getUserEmail()))
            user.setUserEmail(updateInfo.getUserEmail());

        if(StringUtils.hasText(updateInfo.getUserDesc()))
            user.setUserDesc(updateInfo.getUserDesc());

        if(StringUtils.hasText(updateInfo.getUserPwd()))
            user.setUserPwd(passwordEncoder.encode(updateInfo.getUserPwd()));

        return user;
    }

    @Override
    @Transactional
    public User updateUserImage(MultipartFile userImage, String userId) {
        User user = userRepositorySupport.findUserByUserId(userId).get();
        Long userSeq = user.getUserSeq();
        String path = "/opt/upload/user/" + userSeq.toString();

        // 이미지가 없으면 이미지 변경 실패
        if(userImage == null)
            return null;

        String extension = FilenameUtils.getExtension(userImage.getOriginalFilename());

        String fileName = userImage.getName();
        if(fileName.isBlank())
            throw new NullPointerException("File Name is Blank");

        // png, jpg, jpeg 아니면 거르기
        HashSet<String> candidate = new HashSet<>();
        candidate.add("png");
        candidate.add("jpg");
        candidate.add("jpeg");

        if (!candidate.contains(extension.toLowerCase()))
            throw new RuntimeException("Not Supported File Type");

        File dir = new File(path);
        if(!dir.exists())
            dir.mkdirs();

        // 환경에 따라 경우 아래 두 라인의 주석 변경
//         File file = new File(path + "\\img");  // windows 환경
        File file = new File(path + "/userImage." + extension);  // linux 환경

        if (file.exists()) file.delete();

        try {
            userImage.transferTo(file);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        user.setUserProfile("https://sawyou.kro.kr/upload/user/" + userSeq.toString() + "/userImage." + extension);

        return userRepository.save(user);
    }

    // 유저 팔로잉/취소
    @Override
    @Transactional
    public boolean followingUser(User user, Long followingToSeq) {
        // user = 본인, followingToSeq = 팔로잉할 상대 Seq
        // followingFromSeq == followerToSeq  /  followingToSeq == followerFromSeq
        User followingUser = userRepositorySupport.findUserByUserSeq(followingToSeq).get();

        //followingRepositorySupport.findFollowingByUserSeq(상대 Seq, 본인 Seq)
        Optional<Following> following = followingRepositorySupport.findFollowingByUserSeqAndFromSeq(followingToSeq, user.getUserSeq());

        if(following.isPresent()) {
            /*
             * 1. 팔로잉이 존재하면
             * 2. 팔로워 테이블에서 삭제하고
             * 3. 팔로잉 테이블에서 삭제
            */
            //followerRepositorySupport.findFollowerByUserSeq(상대 Seq, 본인 Seq)
            Optional<Follower> follower = followerRepositorySupport.findFollowerByUserSeqAndToSeq(followingToSeq, user.getUserSeq());
            if(follower.isPresent())
                followerRepository.delete(follower.get());

            followingRepository.delete(following.get());
            return false;
        } else {
            /*
             * 1. 팔로잉이 존재하지 않으면
             * 2. 팔로워 테이블에 추가하고
             * 3. 팔로잉 테이블에 추가
             */
            Follower newFollower = Follower.builder().followerFromSeq(followingToSeq).user(user).build();
            followerRepository.save(newFollower);
            Following newFollowing = Following.builder().followingToSeq(followingToSeq).user(user).build();
            followingRepository.save(newFollowing);
            return true;
        }
    }

    // 유저의 팔로잉 리스트 조회
    @Override
    public List<UserListRes> getUserFollowingList(String userId) {
        User user = userRepositorySupport.findUserByUserId(userId).get();

        return followingRepository.findByUser_UserSeq(user.getUserSeq()).stream().map(following -> {
            Long followingUserSeq = following.getFollowingToSeq();
            User followingUser = userRepositorySupport.findUserByUserSeq(followingUserSeq).get();
            return UserListRes.builder()
                    .userSeq(followingUser.getUserSeq())
                    .userId(followingUser.getUserId())
                    .userName(followingUser.getUserName())
                    .userProfile(followingUser.getUserProfile())
                    .build();
        }).collect(Collectors.toList());
    }

    // 유저의 팔로워 리스트 조회
    @Override
    public List<UserListRes> getUserFollowerList(String userId) {
        User user = userRepositorySupport.findUserByUserId(userId).get();

        return followerRepository.findByFollowerFromSeq(user.getUserSeq()).stream().map(follower -> {
            User followerUser = follower.getUser();
            return UserListRes.builder()
                    .userSeq(followerUser.getUserSeq())
                    .userId(followerUser.getUserId())
                    .userName(followerUser.getUserName())
                    .userProfile(followerUser.getUserProfile())
                    .build();
        }).collect(Collectors.toList());
    }

    // 회원 탈퇴
    @Override
    @Transactional
    public User deleteUser(User user) {
        // TODO : 댓글 삭제 처리 어떤 방식으로 할 건지 고민 (댓글의 isDelete true / 댓글은 남기고 유저 아이디를 클릭했을 때 찾을 수 없는 유저 표시)
        /*
         * 1. 유저의 팔로워/팔로잉 삭제
         *    1-1. 팔로잉 테이블에서 fromSeq, toSeq가 userSeq인 거 찾아서 모두 삭제
         *    1-2. 팔로워 테이블에서 fromSeq, toSeq가 userSeq인 거 찾아서 모두 삭제
         * 2. 유저의 게시글/댓글 좋아요 삭제
         * 3. 댓글 삭제
         * 4. 유저의 게시글 중 NFT화 되지 않은 게시글 리스트를 찾고
         *    4-1. 해당 게시글에 속한 댓글의 좋아요 delete
         *    4-2. 해당 게시글에 속한 댓글 isDelete true 처리
         *    4-3. 해당 게시글의 좋아요 delete
         *    4-4. 해당 게시글 isDelete true 처리
         * 5. 유저 isDelete true 처리
         */
        Long userSeq = user.getUserSeq();

        // 1
        followingRepositorySupport.deleteFollowingByFromSeq(userSeq);
        followingRepository.deleteByFollowingToSeq(userSeq);

        followerRepositorySupport.deleteFollowerByToSeq(userSeq);
        followerRepository.deleteByFollowerFromSeq(userSeq);

        // 2
        commentLikeRepositorySupport.deleteCommentLikeByUserSeq(userSeq);
        postLikeRepositorySupport.deletePostLikeByUserSeq(userSeq);

        // 3 - 댓글 삭제 처리는 어떻게 할지 고민
        commentRepositorySupport.findAllByUserSeq(userSeq).forEach(comment -> comment.setCommentIsDelete(true));

        // 4
        List<Post> posts = postRepositorySupport.findPostNotNFTByUserSeq(userSeq);
        posts.forEach(post -> {
            post.getComments().forEach(comment -> {
                    comment.getCommentLikes().forEach(commentLike -> commentLikeRepository.delete(commentLike));
                    comment.setCommentIsDelete(true);
            });
            post.getPostLikes().forEach(postLike -> postLikeRepository.delete(postLike));
            post.setPostIsDelete(true);
        });

        // 5
        user.setUserIsDelete(true);

        return user;
    }
}
