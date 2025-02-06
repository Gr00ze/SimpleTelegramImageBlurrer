package tutorial;

import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;


public class BlurrerBot extends BasicBot {
    Map<String, ArrayList<Integer>> mediaGroupMessages = new HashMap<>();
    Map<String, ArrayList<String>> mediaGroupPhotos = new HashMap<>();
    Deque<String> mediaGroupIdsQueue = new LinkedList<>();
    static final Integer CACHE_SIZE = 10;

    public BlurrerBot(String TOKEN) {
        super(TOKEN);
    }


    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage())
            onMessageReceived(update.getMessage());
    }


    protected void onMessageReceived(Message message){
        //Should be private chat
        if(message.isUserMessage()){
            userMessageBehaviour(message);

        }else if(message.isGroupMessage() || message.isSuperGroupMessage()){
            //new behaviour to develop
            groupMessageBehaviour(message);
        }

    }
    private void userMessageBehaviour(Message message) {
        String mediaGroupId = message.getMediaGroupId();
        if (message.hasPhoto()) {
            if(mediaGroupId == null){
                //If the message received is a "photo" type, it'll echo it but blurred
                //If is only 1 photo it will be sent
                this.sendPhotoWithSpoiler(message.getChatId().toString(),message.getPhoto());
            }else{
                this.handlePhotoAsMediaGroup(message.getChatId().toString(),mediaGroupId, message.getPhoto());


            }

        }else if (message.hasText()) {
            SendMessage sendMessage = new SendMessage();            // Create a SendMessage object with mandatory fields
            sendMessage.setChatId(message.getChatId().toString());
            sendMessage.setText("This bot handles only images.");   //  Custom text upon receiving a text message
            try {
                execute(sendMessage);                               // Call method to send the message
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private void handlePhotoAsMediaGroup(String chatId, String mediaGroupId, List<PhotoSize> photoSizeList) {
        this.mediaGroupIdsQueue.add(mediaGroupId);
        this.mediaGroupMessages.putIfAbsent(mediaGroupId, new ArrayList<>());
        this.mediaGroupPhotos.putIfAbsent(mediaGroupId, new ArrayList<>());

        //Check to save the server from explode
        if(mediaGroupIdsQueue.size() > BlurrerBot.CACHE_SIZE){
            String toRemoveCachedMediaGroupId = this.mediaGroupIdsQueue.pop();
            this.mediaGroupMessages.remove(toRemoveCachedMediaGroupId);
            this.mediaGroupPhotos.remove(toRemoveCachedMediaGroupId);
        }

        //Both populated when at least one message of relative media group id is sent
        ArrayList<Integer> toDeleteMessageIDs = this.mediaGroupMessages.get(mediaGroupId);
        ArrayList<String> sentPhotoFileIds = this.mediaGroupPhotos.get(mediaGroupId);

        if(toDeleteMessageIDs.isEmpty()){
            //The first photo it will be sent
            Message sentMessage = this.sendPhotoWithSpoiler(chatId,photoSizeList);
            //And it will be saved
            if(sentMessage == null)return;
            //save
            toDeleteMessageIDs.add(sentMessage.getMessageId());
            sentPhotoFileIds.add(getPhotoMaxResolutionFileId(photoSizeList));

        }else{
            //If not the first photo then the first will be deleted, and it will be all sent it again



            //Delete already sendedBlurred images
            this.sendDeleteMessagesOfSameMediaGroupId(chatId,toDeleteMessageIDs);
            //Media List
            List<InputMedia> mediaList = new ArrayList<>();
            //Add fileId of deleted message to send it again
            for (String photoFileId : sentPhotoFileIds){
                mediaList.add(this.blurredMediaPhotoByFileId(photoFileId));
            }
            //Add file id of last photo
            mediaList.add(this.blurredMediaPhotoByFileId(this.getPhotoMaxResolutionFileId(photoSizeList)));

            //Make mediagroup
            SendMediaGroup toSendmediaGroup = new SendMediaGroup();
            toSendmediaGroup.setChatId(chatId);
            toSendmediaGroup.setMedias(mediaList);

            //mediaGroup.setProtectContent(true);
            //mandare il mediagroup
            try {
                List<Message> sentMessages = execute(toSendmediaGroup);
                //save
                sentPhotoFileIds.add(this.getPhotoMaxResolutionFileId(photoSizeList));
                for( Message sentMessage: sentMessages){
                    toDeleteMessageIDs.add(sentMessage.getMessageId());
                }
            }catch (TelegramApiException e){
                e.printStackTrace();
            }
        }
    }


    private void groupMessageBehaviour(Message message) {
        //if u reply the photo with bot username it on group chat
        if(message.hasText() && message.getText().contains(this.getBotUsername()) && message.isReply()){
            Message replyToMessage = message.getReplyToMessage();
            if (replyToMessage.hasPhoto())
                this.sendPhotoWithSpoiler(message.getChatId().toString(),replyToMessage.getPhoto());
        }
    }

    private Message sendPhotoWithSpoiler(String chatId, List<PhotoSize> photos) {
        String photoFileId = getPhotoMaxResolutionFileId(photos);
        return this.sendBlurredPhotoByFileId(chatId,photoFileId);
    }
    /**Takes only the largest photo for best quality (usually the last)**/
    private String getPhotoMaxResolutionFileId(List<PhotoSize> photos){

        return photos.get(photos.size() - 1).getFileId();
    }


    private void sendDeleteMessagesOfSameMediaGroupId(String chatId, ArrayList<Integer> toDeleteMessageIDs) {
        Iterator<Integer> iterator = toDeleteMessageIDs.iterator();
        while (iterator.hasNext()){
            Integer toDeleteMessageID = iterator.next();

            try {
                execute(new DeleteMessage(chatId,toDeleteMessageID));
                iterator.remove();
            }catch (TelegramApiException e){
                e.printStackTrace();
            }
        }
    }


    public Message sendBlurredPhotoByFileId(String chatId, String photoFileId){
        try{
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId);                            //  Syncs it with the Chat's ID
        sendPhoto.setPhoto(new InputFile(photoFileId));         //  Creates the new "photo" file to be sent
        sendPhoto.setHasSpoiler(true);                          //  Sets the .setHasSpoiler(Boolean) tag to true
        return execute(sendPhoto);
        }catch (TelegramApiException e){
            e.printStackTrace();
        }
        return null;
    };

    public InputMediaPhoto blurredMediaPhotoByFileId(String photoFileId){
        InputMediaPhoto inputMediaPhoto = new InputMediaPhoto();
        inputMediaPhoto.setMedia(photoFileId);
        inputMediaPhoto.setHasSpoiler(true);
        return inputMediaPhoto;
    }


}
