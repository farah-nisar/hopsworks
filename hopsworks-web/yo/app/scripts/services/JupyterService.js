'use strict';

angular.module('hopsWorksApp')
        .factory('JupyterService', ['$http', function ($http) {
            return {
              getAll: function (projectId) {
                return $http.get('/api/project/' + projectId + '/jupyter');
              },
              get: function (projectId) {
                return $http.get('/api/project/' + projectId + '/jupyter/running' );
              },
              delete: function (projectId) {
                return $http.delete('/api/project/' + projectId + '/jupyter/stop');
              },
              start: function (projectId, sparkConfig) {
                var req = {
                  method: 'POST',
                  url: '/api/project/' + projectId + '/jupyter/start',
                  headers: {
                    'Content-Type': 'application/json'
                  },
                  data: sparkConfig
                };
                return $http(req);
                
//                return $http.get('/api/project/' + projectId + '/jupyter/start' );
              },
              stop: function (projectId) {
                return $http.delete('/api/project/' + projectId + '/jupyter/stop' );
              }              
            };
          }]);
