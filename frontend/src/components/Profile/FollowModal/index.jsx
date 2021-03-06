import React, { useState, useEffect }  from 'react';
import Modal from '@mui/material/Modal';
import Box from '@mui/material/Box';
import { useParams } from 'react-router';
import Avatar from '@mui/material/Avatar';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import {ReadFollowingUser} from "../../../api/user"
import CloseIcon from '@mui/icons-material/Close';
import { useNavigate  } from 'react-router-dom';

const style = {
  position: 'absolute',
  top: '50%',
  left: '50%',
  transform: 'translate(-50%, -50%)',
  width: '400px',
  height: '400px',
  bgcolor: 'background.paper',
  display: 'flex',
  flexDirection: 'column',
  overflow: 'auto'
};

const image2 = {
  height: "30px",
  width: "30px"
}

const Postmodal = (props) => {
  const params = useParams().id;
  const [open, setOpen] = React.useState(false);
  const data = props.item;
  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);
  const [follower, setFollower] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    Read()

  }, [open]);

  const Read = async () => {
    const res = await ReadFollowingUser(params).then((res) => setFollower(res.data.data))
  }

  const onClickRedirectPathHandler = name => e => {
    window.scrollTo(0, 0);
    navigate(`${name}`);
    handleClose()
    window.location.reload()
  };


  const newpost = (
    <Box sx={style} component="form">
        <Box sx = {{height:'8%', display: 'flex', justifyContent:'space-between'}}>
        <Box sx={{ color : 'black' }}></Box>
        <Typography sx={{ mt: 0.8, ml: 2 }}>
          팔로잉
        </Typography>
        <Button onClick={handleClose}
          sx={{ color : 'black', minWidth: '30px' }}
          style={{ padding: '0px' }}
        >
          <CloseIcon></CloseIcon>
        </Button>
      </Box>
      {follower && follower.map((item) =>
        <Button style={{textTransform: 'lowercase'}} sx={{justifyContent:'left'}} onClick={onClickRedirectPathHandler(`/profile/${item.userId}`)}>
          { item.userProfile ? <Avatar style={image2} src={item.userProfile} /> : <img style={image2} src="/images/baseimg_nav.jpg" />}
        <Box sx={{ mt: 0.8, ml: 1 }}><Typography>{item.userId} ({item.userName})</Typography></Box>
      </Button>)
      }

    </Box>
  );
  return (
    <div>
      <Button
        sx={{justifyContent:'left'}}
        key={"add"}
        onClick={handleOpen}
        style={{
          font: "16px",
          color : "black",
          minWidth: "100px",
          minHeight: "24px",
          padding: "0 0 0 0px"
        }}>
          <Typography>팔로잉 {data.followingCnt}</Typography>
        </Button>
      <Modal
      open={open}
      onClose={handleClose}
      aria-labelledby="modal-modal-title"
      aria-describedby="modal-modal-description"
      closeAfterTransition
      >
        {newpost}
      </Modal>
    </div>
  )
}

export default Postmodal;