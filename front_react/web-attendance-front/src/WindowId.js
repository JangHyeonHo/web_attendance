export default function WindowId(key){

    const windowId = {index  : "W000"
                    , login  : "W001"
                    , logout : "W002" 
                    , signup : "W003"
                    , admin  : "W004"
                    , attendance : "W005"};

    
    return windowId[key];
}