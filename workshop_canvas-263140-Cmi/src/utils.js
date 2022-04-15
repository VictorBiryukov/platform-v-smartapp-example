// Получение данных о пользователях:
// https://developer.sberdevices.ru/docs/ru/developer_tools/flow/advanced/variables/raw_request
// Smart App API - объект Character:
// https://developer.sberdevices.ru/docs/ru/developer_tools/amp/smartappapi_description_and_guide#объект-character
function getCharacterName($request) {
    try {
        // Possible names: Сбер, Афина, Джой
        return $request.rawRequest.payload.character.name;
    } catch (e) {
        if ($request.channelType === "chatwidget") {
            return "Джой";
        }
        throw e.message;
    }
}

function answerPush(reply) {
    // если response.replies не существует - создаём пустой элемент массива:
    $jsapi.context().response.replies = $jsapi.context().response.replies || [];
    // добавляем reply в ответ response.replies:
    $jsapi.context().response.replies.push(reply);
}

function findNumber(text) {
    var textArr = text.split(' ');
    for (var i = 0; i < textArr.length; i++) {
        if (Number(textArr[i])) return Number(textArr[i])
    }
}

function getWord(number) {
    $http.config({
                    authService: {
                        service: "platformV",
                        app_key: "$smartClientID",
                        app_secret: "$smartSecret"
                    }
                });
                var url = "https://smapi.pv-api.sbc.space/fn_a02527d4_6dcc_4e7f_8b9d_0a3374ba3a62/anagram/getWordByLength?length=" + number;
                var options = {
                    headers: {
                        "Content-Type": "application/json"
                    }
                }
                var response = $http.get(url, options);
                return response.isOk ? response.data : false;
}

function sendingCandidate(candidate, word) {
    var url = "https://smapi.pv-api.sbc.space/fn_a02527d4_6dcc_4e7f_8b9d_0a3374ba3a62/anagram/checkSubWord?word=" +word +"&candidate=" + candidate.toLowerCase();
    var options = {
        header: {
            "Content-Type": "application/json"
        }
    }
    
    var response = $http.get(url, options);
    return response.isOk ? response.data : false;
}

function reply(body, response) {
    var replyData = {
        type: "raw",
        body: body
    };    
    response.replies = response.replies || [];
    response.replies.push(replyData);
}

function sendAction(action, context) {
    var command = {
        type: "smart_app_data",
        action: action
    };
    return reply({items: [{command: command}]}, context.response);
}

function answerPush(reply) {
    // если response.replies не существует - создаём пустой элемент массива:
    $jsapi.context().response.replies = $jsapi.context().response.replies || [];
    // добавляем reply в ответ response.replies:
    $jsapi.context().response.replies.push(reply);
}