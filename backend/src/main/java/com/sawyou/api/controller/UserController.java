package com.sawyou.api.controller;

import com.sawyou.api.request.UserLoginPostReq;
import com.sawyou.api.request.UserUpdateInfoReq;
import com.sawyou.api.request.UserUpdatePwdReq;
import com.sawyou.api.response.UserListRes;
import com.sawyou.api.response.UserLoginPostRes;
import com.sawyou.common.model.response.Result;
import com.sawyou.common.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.sawyou.api.request.UserRegisterPostReq;
import com.sawyou.api.response.UserRes;
import com.sawyou.api.service.UserService;
import com.sawyou.common.auth.SawyouUserDetails;
import com.sawyou.common.model.response.BaseResponseBody;
import com.sawyou.db.entity.User;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;
import java.util.Map;

/**
 * 유저 관련 API 요청 처리를 위한 컨트롤러 정의.
 */
@Api(value = "유저 API", tags = {"User"})
@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/signup")
    @ApiOperation(value = "회원 가입", notes = "<strong>아이디와 패스워드</strong>를 통해 회원가입 한다.")
    @ApiResponses({
            @ApiResponse(code = 201, message = "성공"),
            @ApiResponse(code = 401, message = "인증 실패"),
            @ApiResponse(code = 404, message = "사용자 없음"),
            @ApiResponse(code = 500, message = "서버 오류")
    })
    public ResponseEntity<? extends BaseResponseBody> register(
            @RequestBody @ApiParam(value = "회원가입 정보", required = true) UserRegisterPostReq registerInfo) {

        //임의로 리턴된 User 인스턴스. 현재 코드는 회원 가입 성공 여부만 판단하기 때문에 굳이 Insert 된 유저 정보를 응답하지 않음.
        User user = userService.createUser(registerInfo);

        return ResponseEntity.status(201).body(BaseResponseBody.of(201, "회원가입 성공"));
    }

    @GetMapping("/idcheck")
    @ApiOperation(value = "아이디 중복 검사", notes = "현재 입력된 아이디가 이미 가입되어있는 아이디인지 확인한다.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "성공"),
            @ApiResponse(code = 404, message = "사용자 없음"),
            @ApiResponse(code = 500, message = "서버 오류")
    })
    public ResponseEntity<Boolean> idCheck(@RequestBody @ApiParam(value = "중복체크할 아이디", required = true) Map<String, String> request) {
        User user = userService.getUserByUserId(request.get("userId"));
        // 중복 아이디가 없는 경우 true 반환
        if(user == null) return ResponseEntity.ok(true);
        // 중복 아이디가 있을 경우 false 반환
        return ResponseEntity.status(409).body(false);
    }

    @PostMapping("/login")
    @ApiOperation(value = "로그인", notes = "<strong>아이디와 패스워드</strong>를 통해 로그인 한다.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "성공", response = UserLoginPostRes.class),
            @ApiResponse(code = 401, message = "인증 실패", response = BaseResponseBody.class),
            @ApiResponse(code = 404, message = "사용자 없음", response = BaseResponseBody.class),
            @ApiResponse(code = 500, message = "서버 오류", response = BaseResponseBody.class)
    })
    public ResponseEntity<UserLoginPostRes> login(@RequestBody @ApiParam(value = "로그인 정보", required = true) UserLoginPostReq loginInfo) {
        String userId = loginInfo.getUserId();
        String password = loginInfo.getUserPwd();

        User user = userService.getUserByUserId(userId);
        if(user.isUserIsDelete()) {
            // 삭제된 회원이라면 로그인 불가능
            return ResponseEntity.status(401).body(UserLoginPostRes.of(401, "삭제된 회원", null));
        }

        // 로그인 요청한 유저로부터 입력된 패스워드 와 디비에 저장된 유저의 암호화된 패스워드가 같은지 확인.(유효한 패스워드인지 여부 확인)
        if (passwordEncoder.matches(password, user.getUserPwd())) {
            // 유효한 패스워드가 맞는 경우, 로그인 성공으로 응답.(액세스 토큰을 포함하여 응답값 전달)
            return ResponseEntity.ok(UserLoginPostRes.of(200, "Success", JwtTokenUtil.getToken(userId)));
        }
        // 유효하지 않는 패스워드인 경우, 로그인 실패로 응답.
        return ResponseEntity.status(401).body(UserLoginPostRes.of(401, "Invalid Password", null));
    }

    @GetMapping("/{userSeq}")
    @ApiOperation(value = "프로필 조회", notes = "회원 정보를 응답한다.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "성공"),
            @ApiResponse(code = 401, message = "인증 실패"),
            @ApiResponse(code = 404, message = "사용자 없음"),
            @ApiResponse(code = 500, message = "서버 오류")
    })
    public ResponseEntity<Result> getUserInfo(@ApiIgnore Authentication authentication, @ApiParam(value = "조회할 유저 일련번호", required = true) @PathVariable Long userSeq) {
        /**
         * 요청 헤더 액세스 토큰이 포함된 경우에만 실행되는 인증 처리이후, 리턴되는 인증 정보 객체(authentication) 통해서 요청한 유저 식별.
         * 액세스 토큰이 없이 요청하는 경우, 403 에러({"error": "Forbidden", "message": "Access Denied"}) 발생.
         */
        SawyouUserDetails userDetails = (SawyouUserDetails) authentication.getDetails();
        String userId = userDetails.getUsername();
        User user = userService.getUserByUserId(userId);
        if(user == null) return ResponseEntity.status(401).body(Result.builder().message("인증실패").build());

        UserRes userRes = userService.getUser(userSeq, user.getUserSeq());
        if(userRes == null) return ResponseEntity.status(404).body(Result.builder().status(404).message("사용자 없음").build());

        return ResponseEntity.status(200).body(Result.builder()
                        .data(userRes).status(200).message("프로필 조회 성공").build());
    }

    @PatchMapping
    @ApiOperation(value = "프로필 수정", notes = "유저의 프로필 정보를 수정한다.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "성공"),
            @ApiResponse(code = 401, message = "인증 실패"),
            @ApiResponse(code = 404, message = "사용자 없음"),
            @ApiResponse(code = 500, message = "서버 오류")
    })
    public ResponseEntity<UserLoginPostRes> updateUserInfo(@ApiIgnore Authentication authentication, @RequestBody @ApiParam(value = "수정할 유저 정보", required = true) UserUpdateInfoReq updateInfo) {
        SawyouUserDetails userDetails = (SawyouUserDetails) authentication.getDetails();
        String userId = userDetails.getUsername();
        User oUser = userService.getUserByUserId(userId);
        if(oUser == null) return ResponseEntity.status(401).body(UserLoginPostRes.of(401, "Invalid Password", null));

        User user = userService.updateUserInfo(updateInfo, oUser.getUserSeq());

        return ResponseEntity.ok(UserLoginPostRes.of(200, "Success", JwtTokenUtil.getToken(user.getUserId())));
    }

    @PatchMapping("/pwd")
    @ApiOperation(value = "비밀번호 수정", notes = "유저의 비밀번호를 수정한다.")
    @ApiResponses({
            @ApiResponse(code = 204, message = "성공"),
            @ApiResponse(code = 401, message = "인증 실패"),
            @ApiResponse(code = 404, message = "사용자 없음"),
            @ApiResponse(code = 500, message = "서버 오류")
    })
    public ResponseEntity<Result> updateUserPwd(@ApiIgnore Authentication authentication, @RequestBody @ApiParam(value = "변경할 비밀번호", required = true) UserUpdatePwdReq updatePwd) {
        SawyouUserDetails userDetails = (SawyouUserDetails) authentication.getDetails();
        String userId = userDetails.getUsername();
        User oUser = userService.getUserByUserId(userId);
        if(oUser == null) return ResponseEntity.status(401).body(Result.builder().message("인증실패").build());

        User user = userService.updateUserPwd(updatePwd, oUser.getUserSeq());
        return ResponseEntity.status(204).body(Result.builder().data(user).status(204).message("비밀번호 수정 성공").build());

    }


    @PatchMapping("/following/{followingToSeq}")
    @ApiOperation(value = "팔로잉/취소", notes = "유저를 팔로잉/취소한다.")
    @ApiResponses({
            @ApiResponse(code = 204, message = "성공"),
            @ApiResponse(code = 401, message = "인증 실패"),
            @ApiResponse(code = 404, message = "사용자 없음"),
            @ApiResponse(code = 500, message = "서버 오류")
    })
    public ResponseEntity<Result> followingUser(@ApiIgnore Authentication authentication, @PathVariable @ApiParam(value = "팔로잉(취소)할 유저", required = true) Long followingToSeq) {
        SawyouUserDetails userDetails = (SawyouUserDetails) authentication.getDetails();

        String userId = userDetails.getUsername();
        User user = userService.getUserByUserId(userId);
        if(user.getUserSeq() == followingToSeq)
            return ResponseEntity.status(409).body(Result.builder().status(409).message("팔로잉 실패").build());

        boolean state = userService.followingUser(user, followingToSeq);

        if(state) return ResponseEntity.status(204).body(Result.builder().status(204).message("팔로잉 성공").build());
        return ResponseEntity.status(204).body(Result.builder().status(204).message("팔로잉 취소").build());
    }

    @DeleteMapping
    @ApiOperation(value = "회원탈퇴", notes = "회원탈퇴를 한다.")
    @ApiResponses({
            @ApiResponse(code = 204, message = "성공"),
            @ApiResponse(code = 401, message = "인증 실패"),
            @ApiResponse(code = 404, message = "사용자 없음"),
            @ApiResponse(code = 500, message = "서버 오류")
    })
    public ResponseEntity<Result> deleteUser(@ApiIgnore Authentication authentication) {
        SawyouUserDetails userDetails = (SawyouUserDetails) authentication.getDetails();
        String userId = userDetails.getUsername();
        User user = userService.getUserByUserId(userId);

        User cUser = userService.deleteUser(user);

        return ResponseEntity.status(204).body(Result.builder().data(cUser).status(200).message("회원 탈퇴 성공").build());
    }

    @GetMapping("/following")
    @ApiOperation(value = "팔로잉 목록 조회", notes = "유저의 팔로잉 목록을 조회한다.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "성공"),
            @ApiResponse(code = 401, message = "인증 실패"),
            @ApiResponse(code = 409, message = "목록 조회 실패"),
            @ApiResponse(code = 500, message = "서버 오류")
    })
    public ResponseEntity<Result> findUserFollowing(@ApiIgnore Authentication authentication) {
        if(authentication == null) return ResponseEntity.status(401).body(Result.builder().status(401).message("인증 실패").build());
        SawyouUserDetails userDetails = (SawyouUserDetails) authentication.getDetails();
        Long userSeq = userDetails.getUser().getUserSeq();
        List<UserListRes> userList = userService.getUserFollowingList(userSeq);

        return ResponseEntity.status(200).body(Result.builder().data(userList).status(200).message("팔로잉 목록 조회 성공").build());
    }

    @GetMapping("/follower")
    @ApiOperation(value = "팔로워 목록 조회", notes = "유저의 팔로워 목록을 조회한다.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "성공"),
            @ApiResponse(code = 401, message = "인증 실패"),
            @ApiResponse(code = 409, message = "목록 조회 실패"),
            @ApiResponse(code = 500, message = "서버 오류")
    })
    public ResponseEntity<Result> findUserFollower(@ApiIgnore Authentication authentication) {
        if(authentication == null) return ResponseEntity.status(401).body(Result.builder().status(401).message("인증 실패").build());
        SawyouUserDetails userDetails = (SawyouUserDetails) authentication.getDetails();
        Long userSeq = userDetails.getUser().getUserSeq();
        List<UserListRes> userList = userService.getUserFollowerList(userSeq);

        return ResponseEntity.status(200).body(Result.builder().data(userList).status(200).message("팔로워 목록 조회 성공").build());
    }

}
