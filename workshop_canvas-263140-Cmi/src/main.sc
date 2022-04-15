require: utils.js

theme: /

    state: Start
        q!: $regex</start>
        event!: RUN_APP_DEEPLINK
        script:
            $session.characterName = getCharacterName($request);
            if ($session.characterName == "Джой") {
                $session.printMessage = "Назови количество букв в главном слове, из букв которого тебе надо составить новые слова";
                $session.mainMessage = "Это игра Анаграмма, где тебе предстоит путем перестановки букв в выбранном слова по количеству букв, создавать новое слово. В нашей базе содержится более 3 млн слов. Сначала выбери количество букв для главного слова, а потом угадывай слова. Удачи!";
            } else {
                $session.printMessage = "Назовите количество букв в главном слове, из букв которого вам надо составить новые слова";
                $session.mainMessage = "Это игра Анаграмма, где вам предстоит путем перестановки букв в выбранном слова по количеству букв, создавать новое слово. В нашей базе содержится более 3 млн слов. Сначала выберите количество букв для главного слова, а потом угадывайте слова. Удачи!";
            }
            sendAction({type: "MAIN_MESSAGE", mainMessage: $session.mainMessage}, $context);
            answerPush({ type: "text", text: $session.mainMessage });
            answerPush({ type: "text", text: $session.printMessage });
            
        state: NumberOfLetters
            q: * @duckling.number *
            event!: SET_NUMBER_OF_LETTERS
            script: 
                $session.guessedWords = [];
                if ($request.data.eventData) {
                    $jsapi.log("ok");
                    $session.number = $request.data.eventData.numberOfLetters;
                } else if ($request.rawRequest.payload && $request.rawRequest.payload.message) {
                    $session.number = findNumber($request.rawRequest.payload.message.human_normalized_text);
                } else {
                    $session.number = findNumber($parseTree.text);
                }
                $session.getWord = getWord($session.number);
                $jsapi.log(JSON.stringify($session.getWord));
                if ($session.getWord && $session.getWord.status == "success") {
                    if ($session.characterName == "Джой") {
                        $session.printMessage = "Твое слово «" + $session.getWord.word + "». Назови по очереди слова, которые можно составить из букв этого слова.\nЕсли слов больше нет, скажи «Конец»";
                    } else {
                        $session.printMessage = "Ваше слово «" + $session.getWord.word + "». Назовите по очереди слова, которые можно составить из букв этого слова.\nЕсли слов больше нет, скажите «Конец»";
                    }
                    $session.printMessageSave = $session.printMessage;
                    sendAction({ type: 'GO_GAME_PAGE' , printMessage: $session.printMessage}, $context);
                } else {
                    $session.printMessage = ($session.characterName == "Джой") ? "К сожалению, у нас нет таких слов. Назови другое число" : "К сожалению, у нас нет таких слов. Назовите другое число";
                    sendAction({ type: 'index', errors: true}, $context);
                }
                answerPush({ type: "text", text: $session.printMessage });
        
            state: CandidateWord
                q: *
                event!: SET_NEW_WORD
                script:
                    if ($request.data.eventData) {
                        $session.newWord = $request.data.eventData.newWord;
                    } else if ($request.rawRequest.payload && $request.rawRequest.payload.message) {
                        $session.newWord = $request.rawRequest.payload.message.human_normalized_text;
                    } else {
                        $session.newWord = $parseTree.text;
                    }
                if: $request.rawRequest.payload && $request.rawRequest.payload.message
                    if: $request.rawRequest.payload.message.human_normalized_text == "конец"
                        go!: /Exit
                if: $parseTree.text == "конец"
                    go!: /Exit
                script:
                    //if ($parseTree.text == "конец") { $reactions.transition("/Exit"); }
                    $session.data = sendingCandidate($session.newWord, $session.getWord.word);
                    $jsapi.log(JSON.stringify($session.data));
                    if ($session.guessedWords.indexOf($session.newWord)!==-1) {
                        $session.printMessage = ($session.characterName == "Джой") ? "Такое слово уже было! Давай поищем ещё одно" : "Такое слово уже было! Давайте поищем ещё одно!";
                        sendAction({ type: 'GO_GAME_PAGE', guessedWords: $session.guessedWords, printMessage: $session.printMessageSave}, $context);
                    } else if ($session.data && $session.data.status == "success") {
                        $session.guessedWords.push($session.newWord);
                        $session.printMessage = ($session.characterName == "Джой") ? "Отлично! Давай поищем ещё одно" : "Отлично! Давайте поищем ещё одно!";
                        sendAction({ type: 'GO_GAME_PAGE', guessedWords: $session.guessedWords, printMessage: $session.printMessageSave}, $context);
                    } else if ($session.data && $session.data.status == "not a subword") {
                        $session.printMessage = ($session.characterName == "Джой") ? "Увы, данное слово не входит в заданное. Попробуй ещё раз" : "Увы, данное слово не входит в заданное. Попробуйте ещё раз";
                        sendAction( { type: 'GO_GAME_PAGE', guessedWords: $session.guessedWords, errors: true}, $context);
                    } else {
                        $session.printMessage = ($session.characterName == "Джой") ? "Увы, такого слова не существует в нашем словаре. Попробуй ещё раз" : "Увы, такого слова не существует в нашем словаре. Попробуйте ещё раз"
                        sendAction( { type: 'GO_GAME_PAGE', guessedWords: $session.guessedWords, printMessage: $session.printMessageSave, errors: true}, $context);
                    }
                    answerPush({ type: "text", text: $session.printMessage });
                    
    state: Exit
        script: 
            answerPush({ type: "text", text: "До новых встреч!" });
            var reply = {
                type: "raw",
                body: {
                    "items": [
                    {
                      "command": {
                        "type": "close_app"
                      }
                    }]
                }
            };
            answerPush(reply);
            $jsapi.stopSession();
                
    state: Fallback
        event!: noMatch